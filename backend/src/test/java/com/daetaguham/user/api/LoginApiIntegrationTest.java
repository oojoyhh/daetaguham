package com.daetaguham.user.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:login;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class LoginApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	private User owner;

	@BeforeEach
	void setUp() {
		owner = userRepository.save(User.create(
				"010-1111-2222",
				passwordEncoder.encode("password123"),
				"김효주"
		));
		storeRepository.save(Store.create(
				owner,
				"성수점",
				"아이스크림·디저트",
				null,
				"SEONG7"
		));
	}

	@Test
	void logsInAndUsesTokenToReadMyAccount() throws Exception {
		MvcResult login = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "phone": "010-1111-2222",
						  "password": "password123"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.user.name").value("김효주"))
				.andExpect(jsonPath("$.stores[0].storeName").value("성수점"))
				.andExpect(jsonPath("$.stores[0].myRole").value("OWNER"))
				.andReturn();

		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
		Jwt jwt = jwtDecoder.decode(token);
		assertEquals(owner.getId().toString(), jwt.getSubject());

		mockMvc.perform(get("/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.phone").value("010-1111-2222"))
				.andExpect(jsonPath("$.stores[0].myRole").value("OWNER"));
	}

	@Test
	void rejectsWrongPasswordWithoutRevealingWhichCredentialFailed() throws Exception {
		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "phone": "010-1111-2222",
						  "password": "wrong-password"
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
	}

	@Test
	void rejectsMissingAndInvalidBearerTokens() throws Exception {
		mockMvc.perform(get("/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

		mockMvc.perform(get("/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}
}
