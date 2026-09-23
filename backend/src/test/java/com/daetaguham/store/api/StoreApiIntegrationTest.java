package com.daetaguham.store.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daetaguham.common.security.JwtTokenService;
import com.daetaguham.shift.domain.ShiftTemplateRepository;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

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
		"spring.datasource.url=jdbc:h2:mem:store-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class StoreApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private ShiftTemplateRepository shiftTemplateRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Test
	void ownerCreatesStoreWithInviteCode() throws Exception {
		User owner = saveUser("010-1111-2222", "김효주");

		mockMvc.perform(post("/stores")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "성수점",
						  "category": "아이스크림·디저트",
						  "address": "서울 성동구"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerId").value(owner.getId()))
				.andExpect(jsonPath("$.ownerName").value("김효주"))
				.andExpect(jsonPath("$.inviteCode", matchesPattern("^[A-Z0-9]{6}$")))
				.andExpect(jsonPath("$.approvalRequired").value(true));

		Store createdStore = storeRepository.findAllByOwner_Id(owner.getId()).getFirst();
		assertThat(shiftTemplateRepository.findAllByStore_IdOrderByStartTimeAsc(createdStore.getId()))
				.extracting("name")
				.containsExactly("오픈", "미들", "마감");
	}

	@Test
	void workerJoinsAsPendingMemberAndCannotApplyTwice() throws Exception {
		User owner = saveUser("010-1111-2222", "김효주");
		User worker = saveUser("010-2222-3333", "김민");
		storeRepository.save(Store.create(owner, "성수점", "아이스크림·디저트", null, "SEONG7"));

		mockMvc.perform(post("/stores/join")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"inviteCode\":\"SEONG7\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(worker.getId()))
				.andExpect(jsonPath("$.role").value("WORKER"))
				.andExpect(jsonPath("$.status").value("PENDING"));

		mockMvc.perform(post("/stores/join")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"inviteCode\":\"SEONG7\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_ALREADY_EXISTS"));
	}

	@Test
	void rejectsUnknownInviteCodeAndOwnerSelfJoin() throws Exception {
		User owner = saveUser("010-1111-2222", "김효주");
		storeRepository.save(Store.create(owner, "성수점", "아이스크림·디저트", null, "SEONG7"));

		mockMvc.perform(post("/stores/join")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"inviteCode\":\"NONE99\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("STORE_NOT_FOUND"));

		mockMvc.perform(post("/stores/join")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"inviteCode\":\"SEONG7\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("OWNER_CANNOT_JOIN"));
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, passwordEncoder.encode("password123"), name));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}
}
