package com.daetaguham.user.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:signup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class SignupApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void createsUserWithBcryptPassword() throws Exception {
		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "김효주",
						  "phone": "010-1111-2222",
						  "password": "password123"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("김효주"))
				.andExpect(jsonPath("$.phone").value("010-1111-2222"))
				.andExpect(jsonPath("$.createdAt", not(blankOrNullString())));

		User saved = userRepository.findByPhone("010-1111-2222").orElseThrow();
		assertNotEquals("password123", saved.getPasswordHash());
		assertTrue(passwordEncoder.matches("password123", saved.getPasswordHash()));
	}

	@Test
	void rejectsInvalidPhoneAndShortPassword() throws Exception {
		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "김효주",
						  "phone": "01011112222",
						  "password": "short"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.message", not(blankOrNullString())));
	}

	@Test
	void rejectsDuplicatePhone() throws Exception {
		userRepository.save(User.create(
				"010-3333-4444",
				passwordEncoder.encode("password123"),
				"이미가입"
		));

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "김효주",
						  "phone": "010-3333-4444",
						  "password": "password123"
						}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(409))
				.andExpect(jsonPath("$.errorCode").value("PHONE_ALREADY_EXISTS"));
	}
}
