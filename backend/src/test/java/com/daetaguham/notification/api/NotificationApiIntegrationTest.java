package com.daetaguham.notification.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daetaguham.common.security.JwtTokenService;
import com.daetaguham.notification.domain.Notification;
import com.daetaguham.notification.domain.NotificationRepository;
import com.daetaguham.notification.domain.NotificationType;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:notification-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class NotificationApiIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private StoreRepository storeRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private JwtTokenService jwtTokenService;

	private User owner;
	private User other;
	private Store store;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-9911-1111", "김효주");
		other = saveUser("010-9922-2222", "김민");
		store = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "NOTI01"));
	}

	@Test
	void listsOnlyMineAndSupportsIndividualAndBulkRead() throws Exception {
		Notification first = notificationRepository.save(Notification.create(
				owner, NotificationType.MEMBER_REQUEST, null, store, "김민님이 참여를 신청했어요."));
		notificationRepository.save(Notification.create(
				owner, NotificationType.APPROVAL_NEEDED, null, store, "승인이 필요해요."));
		notificationRepository.save(Notification.create(
				other, NotificationType.CONFIRMED, null, store, "근무가 확정됐어요."));

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.unreadCount").value(2))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.content.length()").value(2));

		mockMvc.perform(put("/me/notifications/{notificationId}/read", first.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOTIFICATION_NOT_FOUND"));

		mockMvc.perform(put("/me/notifications/{notificationId}/read", first.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/me/notifications")
				.param("unreadOnly", "true")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.unreadCount").value(1))
				.andExpect(jsonPath("$.content[0].isRead").value(false));

		mockMvc.perform(put("/me/notifications/read-all")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/me/notifications")
				.param("unreadOnly", "true")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.unreadCount").value(0));
	}

	@Test
	void validatesPagination() throws Exception {
		mockMvc.perform(get("/me/notifications")
				.param("page", "0")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_NOTIFICATION_QUERY"));

		mockMvc.perform(get("/me/notifications")
				.param("size", "101")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_NOTIFICATION_QUERY"));
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, passwordEncoder.encode("password123"), name));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}
}
