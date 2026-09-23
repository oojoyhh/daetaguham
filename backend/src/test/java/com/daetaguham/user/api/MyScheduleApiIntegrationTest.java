package com.daetaguham.user.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.daetaguham.common.security.JwtTokenService;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
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
		"spring.datasource.url=jdbc:h2:mem:my-schedule-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class MyScheduleApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private StoreMemberRepository storeMemberRepository;

	@Autowired
	private ShiftRepository shiftRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenService jwtTokenService;

	private User owner;
	private User worker;
	private Store mainStore;
	private Store helperStore;

	@BeforeEach
	void setUp() {
		owner = userRepository.save(User.create(
				"010-1111-1111", passwordEncoder.encode("password123"), "김효주"));
		worker = userRepository.save(User.create(
				"010-2222-2222", passwordEncoder.encode("password123"), "김민"));
		mainStore = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "SEONG7"));
		helperStore = storeRepository.save(Store.create(owner, "건대점", "디저트", null, "KONKUK"));

		StoreMember membership = StoreMember.request(mainStore, worker);
		membership.decide(MemberStatus.ACTIVE);
		storeMemberRepository.save(membership);
	}

	@Test
	void combinesOwnStoreAndHelperShiftsWithCalculatedStatus() throws Exception {
		shiftRepository.save(Shift.create(
				mainStore,
				worker,
				LocalDateTime.parse("2020-01-02T10:00:00"),
				LocalDateTime.parse("2020-01-02T16:00:00"),
				"오픈",
				owner
		));
		shiftRepository.save(Shift.create(
				helperStore,
				worker,
				LocalDateTime.parse("2030-01-03T16:00:00"),
				LocalDateTime.parse("2030-01-03T22:00:00"),
				"마감",
				owner
		));

		mockMvc.perform(get("/me/shifts")
				.param("from", "2020-01-01")
				.param("to", "2030-01-31")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].storeName").value("성수점"))
				.andExpect(jsonPath("$[0].isHelper").value(false))
				.andExpect(jsonPath("$[0].status").value("DONE"))
				.andExpect(jsonPath("$[1].storeName").value("건대점"))
				.andExpect(jsonPath("$[1].isHelper").value(true))
				.andExpect(jsonPath("$[1].status").value("UPCOMING"));
	}

	@Test
	void replacesReadsAndClearsAvailabilitiesInWeekdayOrder() throws Exception {
		mockMvc.perform(put("/me/availabilities")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "items": [
						    {"dayOfWeek": 6, "startTime": "10:00", "endTime": "22:00"},
						    {"dayOfWeek": 1, "startTime": "18:00", "endTime": "22:00"}
						  ]
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].dayOfWeek").value(1))
				.andExpect(jsonPath("$[0].startTime").value("18:00"))
				.andExpect(jsonPath("$[1].dayOfWeek").value(6));

		mockMvc.perform(get("/me/availabilities")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(put("/me/availabilities")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void rejectsDuplicateWeekdayAndInvalidTime() throws Exception {
		mockMvc.perform(put("/me/availabilities")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "items": [
						    {"dayOfWeek": 2, "startTime": "10:00", "endTime": "18:00"},
						    {"dayOfWeek": 2, "startTime": "18:00", "endTime": "22:00"}
						  ]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_AVAILABILITY"));

		mockMvc.perform(put("/me/availabilities")
				.header(HttpHeaders.AUTHORIZATION, bearer(worker))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "items": [
						    {"dayOfWeek": 3, "startTime": "22:00", "endTime": "10:00"}
						  ]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_AVAILABILITY"));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}
}
