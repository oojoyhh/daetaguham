package com.daetaguham.store.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.daetaguham.store.domain.StoreNotice;
import com.daetaguham.store.domain.StoreNoticeRepository;
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
		"spring.datasource.url=jdbc:h2:mem:notice-overview-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class StoreNoticeAndOverviewApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private StoreMemberRepository storeMemberRepository;

	@Autowired
	private StoreNoticeRepository storeNoticeRepository;

	@Autowired
	private ShiftRepository shiftRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenService jwtTokenService;

	private User owner;
	private User manager;
	private User worker;
	private Store store;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-1111-1100", "김효주");
		manager = saveUser("010-2222-2200", "이진호");
		worker = saveUser("010-3333-3300", "김민");
		User pending = saveUser("010-4444-4400", "박민서");
		store = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "SEONG7"));
		activate(manager, MemberRole.MANAGER);
		activate(worker, MemberRole.WORKER);
		storeMemberRepository.save(StoreMember.request(store, pending));
	}

	@Test
	void managerCreatesNoticeAndWorkerReadsItFromStoreAndMyNotices() throws Exception {
		LocalDate noticeDate = LocalDate.now().plusDays(1);
		MvcResult created = mockMvc.perform(post("/stores/{storeId}/notices", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "noticeDate": "%s",
						  "content": "오후 2시 재료 입고 예정입니다."
						}
						""".formatted(noticeDate)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.createdByName").value("이진호"))
				.andReturn();

		Long noticeId = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
				created.getResponse().getContentAsString(), "$.id").toString());
		storeNoticeRepository.save(StoreNotice.create(
				store, LocalDate.now().minusDays(1), "지난 전달사항", owner));

		mockMvc.perform(get("/stores/{storeId}/notices", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].content").value("오후 2시 재료 입고 예정입니다."));

		mockMvc.perform(get("/me/store-notices")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].storeName").value("성수점"));

		mockMvc.perform(delete("/notices/{noticeId}", noticeId)
				.header(HttpHeaders.AUTHORIZATION, bearer(manager)))
				.andExpect(status().isNoContent());
	}

	@Test
	void rejectsPastNoticeAndWorkerCannotWrite() throws Exception {
		String body = """
				{
				  "noticeDate": "%s",
				  "content": "지난 전달사항"
				}
				""".formatted(LocalDate.now().minusDays(1));

		mockMvc.perform(post("/stores/{storeId}/notices", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_STORE_NOTICE"));

		mockMvc.perform(post("/stores/{storeId}/notices", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "noticeDate": "%s",
						  "content": "작성 불가"
						}
						""".formatted(LocalDate.now())))
				.andExpect(status().isForbidden());
	}

	@Test
	void ownerAndManagerSeeCalculatedTodayOverview() throws Exception {
		LocalDate today = LocalDate.now();
		shiftRepository.save(Shift.create(
				store, worker, today.atTime(10, 0), today.atTime(16, 0), "오픈", owner));
		shiftRepository.save(Shift.create(
				store, null, today.atTime(16, 0), today.atTime(22, 0), "마감", owner));

		mockMvc.perform(get("/stores/{storeId}/summary", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.todayWorkerCount").value(1))
				.andExpect(jsonPath("$.pendingMemberCount").value(1))
				.andExpect(jsonPath("$.emptyShiftCount").value(1))
				.andExpect(jsonPath("$.pendingApprovalCount").value(0))
				.andExpect(jsonPath("$.openShiftCount").value(0))
				.andExpect(jsonPath("$.todayShifts.length()").value(2))
				.andExpect(jsonPath("$.emptyShifts.length()").value(1));

		mockMvc.perform(get("/stores/{storeId}/summary", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isForbidden());
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
