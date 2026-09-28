package com.daetaguham.request.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.daetaguham.common.security.JwtTokenService;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:request-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class RequestApiIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private StoreRepository storeRepository;
	@Autowired private StoreMemberRepository storeMemberRepository;
	@Autowired private ShiftRepository shiftRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private JwtTokenService jwtTokenService;

	private User owner;
	private User worker;
	private User coworker;
	private User outsider;
	private Store store;
	private Shift workerShift;
	private LocalDate baseDate;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-1111-1200", "김효주");
		worker = saveUser("010-2222-2300", "김민");
		coworker = saveUser("010-3333-3400", "이진호");
		outsider = saveUser("010-4444-4500", "박민서");
		store = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "REQ001"));
		activate(worker, MemberRole.WORKER);
		activate(coworker, MemberRole.WORKER);
		baseDate = LocalDate.now().plusDays(10);
		workerShift = shiftRepository.save(Shift.create(
				store, worker, baseDate.atTime(16, 0), baseDate.atTime(22, 0), "마감", owner));
	}

	@Test
	void workerCreatesPublicCoverAndCancelsIt() throws Exception {
		MvcResult created = mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type": "COVER",
						  "shiftId": %d,
						  "mode": "PUBLIC",
						  "scope": "STORE",
						  "reason": "개인 사정"
						}
						""".formatted(workerShift.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("COVER"))
				.andExpect(jsonPath("$.requesterName").value("김민"))
				.andExpect(jsonPath("$.shift.workerName").value("김민"))
				.andExpect(jsonPath("$.shift.openRequest.status").value("OPEN"))
				.andExpect(jsonPath("$.reason").value("개인 사정"))
				.andReturn();
		Long requestId = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
				created.getResponse().getContentAsString(), "$.id").toString());

		mockMvc.perform(get("/requests/{requestId}", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/requests/{requestId}", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"COVER","shiftId":%d,"mode":"PUBLIC","scope":"STORE"}
						""".formatted(workerShift.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("ACTIVE_REQUEST_EXISTS"));

		mockMvc.perform(delete("/shifts/{shiftId}", workerShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SHIFT_HAS_REQUEST"));

		mockMvc.perform(put("/requests/{requestId}/cancel", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELED"));
		mockMvc.perform(put("/requests/{requestId}/cancel", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_STATE"));
	}

	@Test
	void exchangeStoresMultipleAvailableDatesAndRejectsInvalidScope() throws Exception {
		LocalDate first = baseDate.plusDays(2);
		LocalDate second = baseDate.plusDays(4);
		mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type":"EXCHANGE",
						  "shiftId":%d,
						  "mode":"PUBLIC",
						  "scope":"STORE",
						  "availableDates":["%s","%s","%s"]
						}
						""".formatted(workerShift.getId(), second, first, first)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.availableDates.length()").value(2))
				.andExpect(jsonPath("$.availableDates[0]").value(first.toString()))
				.andExpect(jsonPath("$.availableDates[1]").value(second.toString()));

		Shift another = shiftRepository.save(Shift.create(
				store, coworker, baseDate.plusDays(1).atTime(12, 0),
				baseDate.plusDays(1).atTime(18, 0), "미들", owner));
		mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type":"EXCHANGE","shiftId":%d,"scope":"OWNER_STORES",
						  "availableDates":["%s"]
						}
						""".formatted(another.getId(), baseDate.plusDays(5))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
	}

	@Test
	void ownerCreatesOpenShiftAndOverviewCountsIt() throws Exception {
		mockMvc.perform(post("/stores/{storeId}/open-shifts", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "startAt":"%sT10:00:00",
						  "endAt":"%sT16:00:00",
						  "position":"오픈",
						  "scope":"STORE",
						  "message":"급하게 도움을 구해요",
						  "notifyUserIds":[%d]
						}
						""".formatted(baseDate, baseDate, coworker.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.request.type").value("OPEN_SHIFT"))
				.andExpect(jsonPath("$.request.shift.workerId").doesNotExist())
				.andExpect(jsonPath("$.notifiedCount").value(1));

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(1))
				.andExpect(jsonPath("$.content[0].type").value("OPEN_SHIFT_POSTED"))
				.andExpect(jsonPath("$.content[0].storeId").value(store.getId()));

		mockMvc.perform(get("/stores/{storeId}/summary", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openShiftCount").value(1));

		mockMvc.perform(post("/stores/{storeId}/open-shifts", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "startAt":"%sT10:00:00","endAt":"%sT16:00:00",
						  "position":"오픈","notifyUserIds":[%d]
						}
						""".formatted(baseDate.plusDays(1), baseDate.plusDays(1), worker.getId())))
				.andExpect(status().isForbidden());
	}

	@Test
	void directCoverCreatesProposalAndTargetCanDecline() throws Exception {
		MvcResult created = mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"COVER","shiftId":%d,"mode":"DIRECT","targetUserId":%d}
						""".formatted(workerShift.getId(), coworker.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mode").value("DIRECT"))
				.andExpect(jsonPath("$.applications.length()").value(1))
				.andExpect(jsonPath("$.applications[0].applicantName").value("이진호"))
				.andExpect(jsonPath("$.applications[0].status").value("PROPOSED"))
				.andReturn();
		Long requestId = ((Number) com.jayway.jsonpath.JsonPath.read(
				created.getResponse().getContentAsString(), "$.id")).longValue();
		Long applicationId = ((Number) com.jayway.jsonpath.JsonPath.read(
				created.getResponse().getContentAsString(), "$.applications[0].id")).longValue();

		mockMvc.perform(get("/requests/{requestId}", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applications.length()").value(0))
				.andExpect(jsonPath("$.myApplication.status").value("PROPOSED"));

		mockMvc.perform(put("/applications/{applicationId}/response", applicationId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"accept\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.myApplication.status").value("DECLINED"));

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("REJECTED"));

		mockMvc.perform(post("/requests/{requestId}/proposals", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"switchToPublic\":true,\"scope\":\"STORE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("PUBLIC"))
				.andExpect(jsonPath("$.applications[0].status").value("DECLINED"));
	}

	@Test
	void directExchangeStoresCandidateShiftsAndAcceptanceWaitsForApproval() throws Exception {
		Shift coworkerShift = shiftRepository.save(Shift.create(
				store, coworker, baseDate.plusDays(3).atTime(12, 0),
				baseDate.plusDays(3).atTime(18, 0), "미들", owner));
		MvcResult created = mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type":"EXCHANGE","shiftId":%d,"mode":"DIRECT","scope":"STORE",
						  "targetUserId":%d,"targetShiftIds":[%d]
						}
						""".formatted(workerShift.getId(), coworker.getId(), coworkerShift.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.applications[0].offeredShifts[0].id").value(coworkerShift.getId()))
				.andReturn();
		Long applicationId = ((Number) com.jayway.jsonpath.JsonPath.read(
				created.getResponse().getContentAsString(), "$.applications[0].id")).longValue();

		mockMvc.perform(put("/applications/{applicationId}/response", applicationId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accept":true,"offerShiftId":%d}
						""".formatted(coworkerShift.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.myApplication.status").value("SELECTED"))
				.andExpect(jsonPath("$.myApplication.selectedOfferShift.id").value(coworkerShift.getId()));
	}

	@Test
	void publicCoverSupportsApplyWithdrawAndReapply() throws Exception {
		MvcResult requestResult = createPublicRequest("COVER", null);
		Long requestId = ((Number) com.jayway.jsonpath.JsonPath.read(
				requestResult.getResponse().getContentAsString(), "$.id")).longValue();

		MvcResult applied = mockMvc.perform(post("/requests/{requestId}/applications", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"제가 가능해요\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("APPLIED"))
				.andExpect(jsonPath("$.applicantStoreName").value("성수점"))
				.andReturn();
		Long applicationId = ((Number) com.jayway.jsonpath.JsonPath.read(
				applied.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(put("/applications/{applicationId}/withdraw", applicationId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("WITHDRAWN"));

		mockMvc.perform(post("/requests/{requestId}/applications", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"다시 지원할게요\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(applicationId))
				.andExpect(jsonPath("$.status").value("APPLIED"));

		mockMvc.perform(get("/requests/{requestId}", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicantCount").value(1))
				.andExpect(jsonPath("$.applications[0].message").value("다시 지원할게요"));
	}

	@Test
	void publicExchangeAcceptsOnlyOwnShiftOnAvailableDate() throws Exception {
		LocalDate available = baseDate.plusDays(5);
		Shift offered = shiftRepository.save(Shift.create(
				store, coworker, available.atTime(12, 0), available.atTime(18, 0), "미들", owner));
		MvcResult requestResult = createPublicRequest("EXCHANGE", available);
		Long requestId = ((Number) com.jayway.jsonpath.JsonPath.read(
				requestResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(post("/requests/{requestId}/applications", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"offeredShiftIds":[%d],"message":"이 근무와 바꿔요"}
						""".formatted(offered.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.offeredShifts[0].id").value(offered.getId()));

		mockMvc.perform(delete("/shifts/{shiftId}", offered.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SHIFT_HAS_REQUEST"));
	}

	@Test
	void publicCoverSelectionApprovalChangesWorkerAndClosesOtherApplications() throws Exception {
		activate(outsider, MemberRole.WORKER);
		MvcResult requestResult = createPublicRequest("COVER", null);
		Long requestId = jsonLong(requestResult, "$.id");
		Long selectedId = jsonLong(apply(requestId, coworker, null), "$.id");
		apply(requestId, outsider, null);

		mockMvc.perform(put("/requests/{requestId}/selection", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"applicationId":%d}
						""".formatted(selectedId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("APPROVAL_NEEDED"));

		mockMvc.perform(get("/stores/{storeId}/approvals", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].request.id").value(requestId))
				.andExpect(jsonPath("$[0].selectedApplication.id").value(selectedId))
				.andExpect(jsonPath("$[0].changes[0].before.workerName").value("김민"))
				.andExpect(jsonPath("$[0].changes[0].after.workerName").value("이진호"));

		mockMvc.perform(put("/requests/{requestId}/approval", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.shift.workerName").value("이진호"));

		mockMvc.perform(get("/requests/{requestId}", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applications[0].status").value("SELECTED"))
				.andExpect(jsonPath("$.applications[1].status").value("NOT_SELECTED"));

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, bearer(coworker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("CONFIRMED"));
	}

	@Test
	void rejectionRequiresCommentAndReopensRequestWithoutChangingWorker() throws Exception {
		activate(outsider, MemberRole.MANAGER);
		MvcResult requestResult = createPublicRequest("COVER", null);
		Long requestId = jsonLong(requestResult, "$.id");
		Long applicationId = jsonLong(apply(requestId, coworker, null), "$.id");
		select(requestId, applicationId, null);

		mockMvc.perform(put("/requests/{requestId}/approval", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVE\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/requests/{requestId}/approval", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(outsider))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"REJECT\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

		mockMvc.perform(put("/requests/{requestId}/approval", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(outsider))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"decision":"REJECT","comment":"이번 주 근무가 너무 많아요."}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.shift.workerName").value("김민"));

		mockMvc.perform(get("/requests/{requestId}", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applications[0].status").value("REJECTED"))
				.andExpect(jsonPath("$.applications[0].rejectComment")
						.value("이번 주 근무가 너무 많아요."));
	}

	@Test
	void approvedExchangeSwapsBothWorkers() throws Exception {
		LocalDate available = baseDate.plusDays(6);
		Shift offered = shiftRepository.save(Shift.create(
				store, coworker, available.atTime(12, 0), available.atTime(18, 0), "미들", owner));
		MvcResult requestResult = createPublicRequest("EXCHANGE", available);
		Long requestId = jsonLong(requestResult, "$.id");
		Long applicationId = jsonLong(apply(requestId, coworker, offered.getId()), "$.id");
		select(requestId, applicationId, offered.getId());

		mockMvc.perform(put("/requests/{requestId}/approval", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.shift.workerId").value(coworker.getId()));

		mockMvc.perform(get("/requests/{requestId}", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applications[0].selectedOfferShift.workerId").value(worker.getId()));
	}

	@Test
	void openShiftSelectionConfirmsImmediately() throws Exception {
		MvcResult urgent = mockMvc.perform(post("/stores/{storeId}/open-shifts", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "startAt":"%sT10:00:00","endAt":"%sT16:00:00",
						  "position":"오픈","scope":"STORE","notifyUserIds":[%d]
						}
						""".formatted(baseDate.plusDays(2), baseDate.plusDays(2), coworker.getId())))
				.andExpect(status().isCreated())
				.andReturn();
		Long requestId = jsonLong(urgent, "$.request.id");
		Long applicationId = jsonLong(apply(requestId, coworker, null), "$.id");

		mockMvc.perform(put("/requests/{requestId}/selection", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"applicationId":%d}
						""".formatted(applicationId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.shift.workerName").value("이진호"));
	}

	private MvcResult createPublicRequest(String type, LocalDate availableDate) throws Exception {
		String dates = availableDate == null
				? ""
				: ",\"availableDates\":[\"" + availableDate + "\"]";
		return mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"%s","shiftId":%d,"mode":"PUBLIC","scope":"STORE"%s}
						""".formatted(type, workerShift.getId(), dates)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private MvcResult apply(Long requestId, User applicant, Long offeredShiftId) throws Exception {
		String offers = offeredShiftId == null
				? ""
				: "\"offeredShiftIds\":[" + offeredShiftId + "],";
		return mockMvc.perform(post("/requests/{requestId}/applications", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(applicant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{" + offers + "\"message\":\"지원할게요\"}"))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private void select(Long requestId, Long applicationId, Long offerShiftId) throws Exception {
		String offer = offerShiftId == null ? "" : ",\"offerShiftId\":" + offerShiftId;
		mockMvc.perform(put("/requests/{requestId}/selection", requestId)
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"applicationId\":" + applicationId + offer + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
	}

	private Long jsonLong(MvcResult result, String path) throws Exception {
		return ((Number) com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), path)).longValue();
	}

	private void activate(User user, MemberRole role) {
		StoreMember membership = StoreMember.request(store, user);
		membership.decide(MemberStatus.ACTIVE);
		membership.changeRole(role);
		storeMemberRepository.save(membership);
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, passwordEncoder.encode("password123"), name));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}
}
