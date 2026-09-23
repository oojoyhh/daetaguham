package com.daetaguham.store.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daetaguham.common.security.JwtTokenService;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:member-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class MemberManagementApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private StoreMemberRepository storeMemberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenService jwtTokenService;

	private User owner;
	private User manager;
	private User worker;
	private User outsider;
	private Store store;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-1111-1111", "김효주");
		manager = saveUser("010-2222-2222", "이진호");
		worker = saveUser("010-3333-3333", "김민");
		outsider = saveUser("010-4444-4444", "박민서");
		store = storeRepository.save(Store.create(owner, "성수점", "아이스크림·디저트", null, "SEONG7"));

		StoreMember managerMembership = StoreMember.request(store, manager);
		managerMembership.decide(MemberStatus.ACTIVE);
		managerMembership.changeRole(MemberRole.MANAGER);
		storeMemberRepository.save(managerMembership);
		storeMemberRepository.save(StoreMember.request(store, worker));
	}

	@Test
	void ownerListsPendingMembersApprovesAndPromotesWorker() throws Exception {
		mockMvc.perform(get("/stores/{storeId}/members", store.getId())
				.param("status", "PENDING")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].userId").value(worker.getId()))
				.andExpect(jsonPath("$[0].status").value("PENDING"));

		mockMvc.perform(put("/stores/{storeId}/members/{userId}/status", store.getId(), worker.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.joinedAt", not(blankOrNullString())));

		mockMvc.perform(put("/stores/{storeId}/members/{userId}/role", store.getId(), worker.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"MANAGER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("MANAGER"));
	}

	@Test
	void activeManagerCanRejectPendingRequestButCannotChangeRoles() throws Exception {
		mockMvc.perform(put("/stores/{storeId}/members/{userId}/status", store.getId(), worker.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"REJECTED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		mockMvc.perform(put("/stores/{storeId}/members/{userId}/role", store.getId(), manager.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(manager))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"WORKER\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("OWNER_REQUIRED"));
	}

	@Test
	void workerCannotManageMembersAndProcessedRequestCannotBeProcessedAgain() throws Exception {
		mockMvc.perform(get("/stores/{storeId}/members", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("STORE_MANAGEMENT_FORBIDDEN"));

		StoreMember membership = storeMemberRepository.findByStore_IdAndUser_Id(store.getId(), worker.getId())
				.orElseThrow();
		membership.decide(MemberStatus.ACTIVE);

		mockMvc.perform(put("/stores/{storeId}/members/{userId}/status", store.getId(), worker.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"REJECTED\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INVALID_MEMBER_STATE"));
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, passwordEncoder.encode("password123"), name));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}
}
