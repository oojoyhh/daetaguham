package com.daetaguham.shift.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daetaguham.common.security.JwtTokenService;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.shift.domain.ShiftTemplate;
import com.daetaguham.shift.domain.ShiftTemplateRepository;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import java.time.LocalTime;
import java.time.LocalDateTime;

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
		"spring.datasource.url=jdbc:h2:mem:shift-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class ShiftApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private StoreMemberRepository storeMemberRepository;

	@Autowired
	private ShiftTemplateRepository shiftTemplateRepository;

	@Autowired
	private ShiftRepository shiftRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenService jwtTokenService;

	private User owner;
	private User manager;
	private User worker;
	private User helper;
	private Store store;
	private Store otherStore;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-1111-1000", "김효주");
		manager = saveUser("010-2222-2000", "이진호");
		worker = saveUser("010-3333-3000", "김민");
		helper = saveUser("010-4444-4000", "박민서");
		store = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "SEONG7"));
		otherStore = storeRepository.save(Store.create(owner, "건대점", "디저트", null, "KONKUK"));
		activateManager(store, manager);
		activate(store, worker);
		activate(otherStore, helper);
		shiftTemplateRepository.save(ShiftTemplate.create(
				store, "마감", LocalTime.of(16, 0), LocalTime.of(22, 0), 1));
		shiftTemplateRepository.save(ShiftTemplate.create(
				store, "미들", LocalTime.of(12, 0), LocalTime.of(18, 0), 1));
	}

	@Test
	void ownerReplacesTemplatesAndActiveWorkerCanReadThem() throws Exception {
		mockMvc.perform(put("/stores/{storeId}/shift-templates", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						[
						  {"name":"오픈","startTime":"09:00","endTime":"15:00"},
						  {"name":"마감","startTime":"15:00","endTime":"22:00","requiredCount":2}
						]
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("오픈"))
				.andExpect(jsonPath("$[0].startTime").value("09:00"))
				.andExpect(jsonPath("$[0].requiredCount").value(1))
				.andExpect(jsonPath("$[1].requiredCount").value(2));

		mockMvc.perform(get("/stores/{storeId}/shift-templates", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void activeManagerCreatesReadsUpdatesAndDeletesShift() throws Exception {
		MvcResult created = mockMvc.perform(post("/stores/{storeId}/shifts", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workerId": %d,
						  "startAt": "2026-10-02T16:00:00",
						  "endAt": "2026-10-02T22:00:00",
						  "position": "마감"
						}
						""".formatted(worker.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.workerName").value("김민"))
				.andExpect(jsonPath("$.position").value("마감"))
				.andReturn();

		String shiftId = com.jayway.jsonpath.JsonPath.read(
				created.getResponse().getContentAsString(), "$.id").toString();

		mockMvc.perform(get("/stores/{storeId}/shifts", store.getId())
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(Long.valueOf(shiftId)));

		mockMvc.perform(put("/shifts/{shiftId}", shiftId)
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workerId": %d,
						  "startAt": "2026-10-02T15:00:00",
						  "endAt": "2026-10-02T21:00:00",
						  "position": "마감"
						}
						""".formatted(worker.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startAt").value("2026-10-02T15:00:00"));

		mockMvc.perform(delete("/shifts/{shiftId}", shiftId)
				.header(HttpHeaders.AUTHORIZATION, bearer(manager)))
				.andExpect(status().isNoContent());
	}

	@Test
	void rejectsOverlapAndWorkerManagementButAllowsSameOwnerHelper() throws Exception {
		createShift(worker, "2026-10-03T10:00:00", "2026-10-03T16:00:00")
				.andExpect(status().isCreated());

		createShift(worker, "2026-10-03T15:00:00", "2026-10-03T18:00:00")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("TIME_CONFLICT"));

		createShift(helper, "2026-10-04T16:00:00", "2026-10-04T22:00:00")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isHelper").value(true));

		mockMvc.perform(post("/stores/{storeId}/shifts", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "startAt": "2026-10-05T16:00:00",
						  "endAt": "2026-10-05T22:00:00",
						  "position": "마감"
						}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void bulkSavePreservesIdenticalRowsAndSupportsFullReset() throws Exception {
		String body = """
				{
				  "from": "2026-10-01",
				  "to": "2026-10-31",
				  "shifts": [
				    {"workerId": %d, "date": "2026-10-10", "position": "마감"},
				    {"workerId": null, "date": "2026-10-11", "position": "마감"}
				  ]
				}
				""".formatted(worker.getId());

		mockMvc.perform(post("/stores/{storeId}/shifts/bulk", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(2))
				.andExpect(jsonPath("$.deleted").value(0))
				.andExpect(jsonPath("$.emptyShiftIds.length()").value(1));

		mockMvc.perform(post("/stores/{storeId}/shifts/bulk", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(0))
				.andExpect(jsonPath("$.deleted").value(0));

		mockMvc.perform(post("/stores/{storeId}/shifts/bulk", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "from": "2026-10-01",
						  "to": "2026-10-31",
						  "shifts": []
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(0))
				.andExpect(jsonPath("$.deleted").value(2))
				.andExpect(jsonPath("$.emptyShiftIds.length()").value(0));
	}

	@Test
	void bulkSaveRejectsInternalAndOtherStoreConflictsWithoutPartialChanges() throws Exception {
		mockMvc.perform(post("/stores/{storeId}/shifts/bulk", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "from": "2026-10-01",
						  "to": "2026-10-31",
						  "shifts": [
						    {"workerId": %d, "date": "2026-10-12", "position": "미들"},
						    {"workerId": %d, "date": "2026-10-12", "position": "마감"}
						  ]
						}
						""".formatted(worker.getId(), worker.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TIME_CONFLICT"))
				.andExpect(jsonPath("$.conflicts[0].storeName").value("성수점"));

		shiftRepository.save(Shift.create(
				otherStore,
				worker,
				LocalDateTime.parse("2026-10-13T16:00:00"),
				LocalDateTime.parse("2026-10-13T22:00:00"),
				"마감",
				owner
		));

		mockMvc.perform(post("/stores/{storeId}/shifts/bulk", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "from": "2026-10-01",
						  "to": "2026-10-31",
						  "shifts": [
						    {"workerId": %d, "date": "2026-10-13", "position": "마감"}
						  ]
						}
						""".formatted(worker.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TIME_CONFLICT"))
				.andExpect(jsonPath("$.conflicts[0].storeName").value("건대점"));

		mockMvc.perform(get("/stores/{storeId}/shifts", store.getId())
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	private org.springframework.test.web.servlet.ResultActions createShift(
			User assignee,
			String startAt,
			String endAt
	) throws Exception {
		return mockMvc.perform(post("/stores/{storeId}/shifts", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workerId": %d,
						  "startAt": "%s",
						  "endAt": "%s",
						  "position": "마감"
						}
						""".formatted(assignee.getId(), startAt, endAt)));
	}

	private void activate(Store memberStore, User user) {
		StoreMember membership = StoreMember.request(memberStore, user);
		membership.decide(MemberStatus.ACTIVE);
		storeMemberRepository.save(membership);
	}

	private void activateManager(Store memberStore, User user) {
		StoreMember membership = StoreMember.request(memberStore, user);
		membership.decide(MemberStatus.ACTIVE);
		membership.changeRole(MemberRole.MANAGER);
		storeMemberRepository.save(membership);
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, passwordEncoder.encode("password123"), name));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}
}
