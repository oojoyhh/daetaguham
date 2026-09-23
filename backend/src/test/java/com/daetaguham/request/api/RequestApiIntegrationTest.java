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
				.andExpect(jsonPath("$.notifiedCount").value(0));

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
	void directRequestIsRejectedUntilProposalApplicationsAreCreated() throws Exception {
		mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"COVER","shiftId":%d,"mode":"DIRECT","targetUserId":%d}
						""".formatted(workerShift.getId(), coworker.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
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
