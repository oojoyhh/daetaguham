package com.daetaguham.shift.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import com.daetaguham.common.security.JwtTokenService;
import com.daetaguham.request.domain.RequestMode;
import com.daetaguham.request.domain.RequestScope;
import com.daetaguham.request.domain.RequestType;
import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.request.domain.ShiftRequestRepository;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.Availability;
import com.daetaguham.user.domain.AvailabilityRepository;
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
		"spring.datasource.url=jdbc:h2:mem:candidate-recommendation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"app.requests.expiration.initial-delay=PT1H"
})
@AutoConfigureMockMvc
@Transactional
class CandidateRecommendationApiIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private StoreRepository storeRepository;
	@Autowired private StoreMemberRepository storeMemberRepository;
	@Autowired private AvailabilityRepository availabilityRepository;
	@Autowired private ShiftRepository shiftRepository;
	@Autowired private ShiftRequestRepository requestRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private JwtTokenService jwtTokenService;

	private User owner;
	private User requester;
	private User best;
	private User heavy;
	private User busy;
	private User unavailable;
	private User sisterWorker;
	private User outsider;
	private Store store;
	private Store sisterStore;
	private Shift requestedShift;
	private LocalDate targetDate;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-7100-0001", "김효주");
		requester = saveUser("010-7100-0002", "김민");
		best = saveUser("010-7100-0003", "이진호");
		heavy = saveUser("010-7100-0004", "박민서");
		busy = saveUser("010-7100-0005", "정수빈");
		unavailable = saveUser("010-7100-0006", "오하늘");
		sisterWorker = saveUser("010-7100-0007", "한수아");
		outsider = saveUser("010-7100-0008", "최지우");

		store = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "CAND01"));
		sisterStore = storeRepository.save(Store.create(
				owner, "건대점", "디저트", null, "CAND02"));
		activate(store, requester);
		activate(store, best);
		activate(store, heavy);
		activate(store, busy);
		activate(store, unavailable);
		activate(sisterStore, sisterWorker);

		targetDate = LocalDate.now()
				.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
				.plusWeeks(2)
				.plusDays(2);
		requestedShift = shiftRepository.save(Shift.create(
				store,
				requester,
				targetDate.atTime(16, 0),
				targetDate.atTime(22, 0),
				"마감",
				owner));

		available(best);
		available(heavy);
		available(busy);
		available(sisterWorker);
		createHeavyWeek();
		shiftRepository.save(Shift.create(
				store,
				busy,
				targetDate.atTime(18, 0),
				targetDate.atTime(23, 0),
				"마감",
				owner));
		createConfirmedHelpHistory();
	}

	@Test
	void coverCandidatesExcludeConflictsAndRankByWorkloadAndHelpHistory() throws Exception {
		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("type", "COVER")
				.param("scope", "STORE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidates.length()").value(2))
				.andExpect(jsonPath("$.excludedCount").value(2))
				.andExpect(jsonPath("$.candidates[0].name").value("이진호"))
				.andExpect(jsonPath("$.candidates[0].score").value(92))
				.andExpect(jsonPath("$.candidates[0].weekHours").value(0.0))
				.andExpect(jsonPath("$.candidates[0].gaveToMe").value(1))
				.andExpect(jsonPath("$.candidates[0].aiReason", containsString("1번")))
				.andExpect(jsonPath("$.candidates[1].name").value("박민서"))
				.andExpect(jsonPath("$.candidates[1].score").value(76))
				.andExpect(jsonPath("$.candidates[1].weekHours").value(18.0));

		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("type", "COVER")
				.param("scope", "OWNER_STORES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidates.length()").value(3))
				.andExpect(jsonPath("$.candidates[1].name").value("한수아"))
				.andExpect(jsonPath("$.candidates[1].storeName").value("건대점"))
				.andExpect(jsonPath("$.candidates[1].score").value(80));

		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("type", "COVER")
				.param("limit", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidates.length()").value(1))
				.andExpect(jsonPath("$.excludedCount").value(2));
	}

	@Test
	void exchangeCandidatesIncludeOnlyWorkersWithAConflictFreeShiftToOffer() throws Exception {
		User noOffer = saveUser("010-7100-0009", "윤다은");
		activate(store, noOffer);
		available(noOffer);
		Shift offered = shiftRepository.save(Shift.create(
				store,
				best,
				targetDate.plusDays(2).atTime(12, 0),
				targetDate.plusDays(2).atTime(18, 0),
				"미들",
				owner));

		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("type", "EXCHANGE")
				.param("scope", "STORE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidates.length()").value(2))
				.andExpect(jsonPath("$.excludedCount").value(3))
				.andExpect(jsonPath("$.candidates[0].name").value("이진호"))
				.andExpect(jsonPath("$.candidates[0].shifts[0].id").value(offered.getId()));

		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("type", "EXCHANGE")
				.param("scope", "OWNER_STORES"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerCanFindOpenShiftCandidatesAcrossOwnedStores() throws Exception {
		mockMvc.perform(get("/stores/{storeId}/candidates", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner))
				.param("startAt", targetDate + "T16:00:00")
				.param("endAt", targetDate + "T22:00:00")
				.param("scope", "OWNER_STORES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidates.length()").value(3))
				.andExpect(jsonPath("$.excludedCount").value(3))
				.andExpect(jsonPath("$.candidates[0].name").value("이진호"))
				.andExpect(jsonPath("$.candidates[0].score").value(100))
				.andExpect(jsonPath("$.candidates[0].aiReason", containsString("최근 급구 참여가 없고")))
				.andExpect(jsonPath("$.candidates[1].name").value("한수아"))
				.andExpect(jsonPath("$.candidates[1].score").value(90));

		mockMvc.perform(get("/stores/{storeId}/candidates", store.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("startAt", targetDate + "T16:00:00")
				.param("endAt", targetDate + "T22:00:00")
				.param("scope", "STORE"))
				.andExpect(status().isForbidden());
	}

	@Test
	void onlyShiftOwnerCanAskAndInvalidRecommendationInputIsRejected() throws Exception {
		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(outsider))
				.param("type", "COVER"))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("type", "OPEN_SHIFT"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/shifts/{shiftId}/candidates", requestedShift.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(requester))
				.param("type", "COVER")
				.param("limit", "21"))
				.andExpect(status().isBadRequest());
	}

	private void createHeavyWeek() {
		LocalDate monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
			LocalDate day = monday.plusDays(dayOffset);
			shiftRepository.save(Shift.create(
					store, heavy, day.atTime(8, 0), day.atTime(14, 0), "오픈", owner));
		}
	}

	private void createConfirmedHelpHistory() {
		LocalDateTime oldStart = LocalDate.now().minusDays(14).atTime(16, 0);
		Shift transferred = Shift.create(
				store, requester, oldStart, oldStart.plusHours(6), "마감", owner);
		transferred.assignWorker(best);
		shiftRepository.save(transferred);
		ShiftRequest history = ShiftRequest.create(
				RequestType.COVER,
				RequestMode.PUBLIC,
				RequestScope.STORE,
				transferred,
				requester,
				"개인 사정");
		history.confirm();
		requestRepository.save(history);
	}

	private void available(User user) {
		int dayOfWeek = targetDate.getDayOfWeek().getValue() % 7;
		availabilityRepository.save(Availability.create(
				user, dayOfWeek, LocalTime.of(10, 0), LocalTime.of(23, 0)));
	}

	private void activate(Store memberStore, User user) {
		StoreMember membership = StoreMember.request(memberStore, user);
		membership.decide(MemberStatus.ACTIVE);
		storeMemberRepository.save(membership);
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(
				phone, passwordEncoder.encode("password123"), name));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user);
	}
}
