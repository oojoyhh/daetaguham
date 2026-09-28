package com.daetaguham.request.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
		"spring.datasource.url=jdbc:h2:mem:request-query-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class RequestQueryApiIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private StoreRepository storeRepository;
	@Autowired private StoreMemberRepository storeMemberRepository;
	@Autowired private ShiftRepository shiftRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private JwtTokenService jwtTokenService;

	private User owner;
	private User requester;
	private User candidate;
	private User outsider;
	private Store store;
	private Shift firstShift;
	private Shift secondShift;
	private LocalDate baseDate;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-8111-1111", "김효주");
		requester = saveUser("010-8222-2222", "김민");
		candidate = saveUser("010-8333-3333", "이진호");
		outsider = saveUser("010-8444-4444", "박민서");
		store = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "LIST01"));
		activate(store, requester);
		activate(store, candidate);
		baseDate = LocalDate.now().plusDays(14);
		firstShift = saveShift(store, requester, baseDate, 16, 22, "마감");
		secondShift = saveShift(store, requester, baseDate.plusDays(1), 12, 18, "미들");
	}

	@Test
	void boardShowsOnlyVisibleFutureRequestsAndSupportsFilters() throws Exception {
		MvcResult created = createRequest(requester, """
				{"type":"COVER","shiftId":%d,"mode":"PUBLIC","scope":"STORE"}
				""".formatted(firstShift.getId()));
		Long requestId = jsonLong(created, "$.id");

		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.content[0].id").value(requestId))
				.andExpect(jsonPath("$.content[0].type").value("COVER"))
				.andExpect(jsonPath("$.content[0].requesterName").value("김민"))
				.andExpect(jsonPath("$.content[0].myApplicationStatus").doesNotExist());

		mockMvc.perform(get("/requests")
				.param("type", "EXCHANGE")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/requests")
				.param("storeId", store.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(requester)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		saveShift(store, candidate, baseDate, 17, 21, "겹침");
		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void exchangeAppearsOnlyWhenCandidateHasShiftOnOfferedDate() throws Exception {
		LocalDate offeredDate = baseDate.plusDays(4);
		MvcResult created = createRequest(requester, """
				{
				  "type":"EXCHANGE","shiftId":%d,"mode":"PUBLIC","scope":"STORE",
				  "availableDates":["%s"]
				}
				""".formatted(firstShift.getId(), offeredDate));
		Long requestId = jsonLong(created, "$.id");

		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		saveShift(store, candidate, offeredDate, 12, 18, "미들");
		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(requestId))
				.andExpect(jsonPath("$.content[0].availableDates[0]").value(offeredDate.toString()));
	}

	@Test
	void myActivitySeparatesSentAppliedAndReceivedAndPaginates() throws Exception {
		MvcResult publicRequest = createRequest(requester, """
				{"type":"COVER","shiftId":%d,"mode":"PUBLIC","scope":"STORE"}
				""".formatted(firstShift.getId()));
		Long publicId = jsonLong(publicRequest, "$.id");

		mockMvc.perform(post("/requests/{requestId}/applications", publicId)
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"지원할게요\"}"))
				.andExpect(status().isCreated());

		MvcResult directRequest = createRequest(requester, """
				{
				  "type":"COVER","shiftId":%d,"mode":"DIRECT","scope":"STORE",
				  "targetUserId":%d
				}
				""".formatted(secondShift.getId(), candidate.getId()));
		Long directId = jsonLong(directRequest, "$.id");

		mockMvc.perform(get("/me/requests")
				.param("box", "sent")
				.param("page", "1")
				.param("size", "1")
				.header(HttpHeaders.AUTHORIZATION, bearer(requester)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].id").value(directId));

		mockMvc.perform(get("/me/requests")
				.param("box", "sent")
				.param("page", "2")
				.param("size", "1")
				.header(HttpHeaders.AUTHORIZATION, bearer(requester)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(publicId));

		mockMvc.perform(get("/me/requests")
				.param("box", "applied")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(publicId))
				.andExpect(jsonPath("$.content[0].myApplicationStatus").value("APPLIED"));

		mockMvc.perform(get("/me/requests")
				.param("box", "received")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(directId))
				.andExpect(jsonPath("$.content[0].myApplicationStatus").value("PROPOSED"));

		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].myApplicationStatus").value("APPLIED"));
	}

	@Test
	void ownerStoresScopeReachesSisterStoreMembersAndValidatesQueries() throws Exception {
		Store sisterStore = storeRepository.save(Store.create(
				owner, "건대점", "디저트", null, "LIST02"));
		User sisterWorker = saveUser("010-8555-5555", "한석휘");
		activate(sisterStore, sisterWorker);
		createRequest(requester, """
				{"type":"COVER","shiftId":%d,"mode":"PUBLIC","scope":"OWNER_STORES"}
				""".formatted(firstShift.getId()));

		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(sisterWorker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].storeName").value("성수점"));

		mockMvc.perform(get("/me/requests")
				.param("box", "unknown")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_QUERY"));

		mockMvc.perform(get("/requests")
				.param("page", "0")
				.header(HttpHeaders.AUTHORIZATION, bearer(candidate)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_QUERY"));
	}

	private MvcResult createRequest(User actor, String body) throws Exception {
		return mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, bearer(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private Shift saveShift(
			Store shiftStore,
			User worker,
			LocalDate date,
			int startHour,
			int endHour,
			String position
	) {
		return shiftRepository.save(Shift.create(
				shiftStore, worker, date.atTime(startHour, 0), date.atTime(endHour, 0), position, owner));
	}

	private void activate(Store activeStore, User user) {
		StoreMember membership = StoreMember.request(activeStore, user);
		membership.decide(MemberStatus.ACTIVE);
		membership.changeRole(MemberRole.WORKER);
		storeMemberRepository.save(membership);
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, passwordEncoder.encode("password123"), name));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}

	private Long jsonLong(MvcResult result, String path) throws Exception {
		return ((Number) com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), path)).longValue();
	}
}
