package com.daetaguham.common.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import com.daetaguham.notification.domain.Notification;
import com.daetaguham.notification.domain.NotificationRepository;
import com.daetaguham.notification.domain.NotificationType;
import com.daetaguham.request.domain.ApplicationOfferShift;
import com.daetaguham.request.domain.ApplicationOfferShiftRepository;
import com.daetaguham.request.domain.RequestApplication;
import com.daetaguham.request.domain.RequestApplicationRepository;
import com.daetaguham.request.domain.RequestAvailableDate;
import com.daetaguham.request.domain.RequestAvailableDateRepository;
import com.daetaguham.request.domain.RequestMode;
import com.daetaguham.request.domain.RequestScope;
import com.daetaguham.request.domain.RequestType;
import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.request.domain.ShiftRequestRepository;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.shift.domain.ShiftTemplate;
import com.daetaguham.shift.domain.ShiftTemplateRepository;
import com.daetaguham.store.application.InviteCodeGenerator;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreNotice;
import com.daetaguham.store.domain.StoreNoticeRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.Availability;
import com.daetaguham.user.domain.AvailabilityRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "seed-enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final ShiftTemplateRepository shiftTemplateRepository;
	private final ShiftRepository shiftRepository;
	private final AvailabilityRepository availabilityRepository;
	private final StoreNoticeRepository storeNoticeRepository;
	private final ShiftRequestRepository shiftRequestRepository;
	private final RequestAvailableDateRepository requestAvailableDateRepository;
	private final RequestApplicationRepository requestApplicationRepository;
	private final ApplicationOfferShiftRepository applicationOfferShiftRepository;
	private final NotificationRepository notificationRepository;
	private final InviteCodeGenerator inviteCodeGenerator;
	private final PasswordEncoder passwordEncoder;
	private final String demoPassword;

	public DemoDataInitializer(
			UserRepository userRepository,
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			ShiftTemplateRepository shiftTemplateRepository,
			ShiftRepository shiftRepository,
			AvailabilityRepository availabilityRepository,
			StoreNoticeRepository storeNoticeRepository,
			ShiftRequestRepository shiftRequestRepository,
			RequestAvailableDateRepository requestAvailableDateRepository,
			RequestApplicationRepository requestApplicationRepository,
			ApplicationOfferShiftRepository applicationOfferShiftRepository,
			NotificationRepository notificationRepository,
			InviteCodeGenerator inviteCodeGenerator,
			PasswordEncoder passwordEncoder,
			@Value("${app.demo.password}") String demoPassword
	) {
		this.userRepository = userRepository;
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.shiftTemplateRepository = shiftTemplateRepository;
		this.shiftRepository = shiftRepository;
		this.availabilityRepository = availabilityRepository;
		this.storeNoticeRepository = storeNoticeRepository;
		this.shiftRequestRepository = shiftRequestRepository;
		this.requestAvailableDateRepository = requestAvailableDateRepository;
		this.requestApplicationRepository = requestApplicationRepository;
		this.applicationOfferShiftRepository = applicationOfferShiftRepository;
		this.notificationRepository = notificationRepository;
		this.inviteCodeGenerator = inviteCodeGenerator;
		this.passwordEncoder = passwordEncoder;
		this.demoPassword = demoPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		User owner = findOrCreateUser("김효주", "010-9000-0001");
		if (storeRepository.existsByOwner_Id(owner.getId())) {
			log.info("Demo data already exists; skipping initialization");
			return;
		}

		User manager = findOrCreateUser("김민", "010-9000-0002");
		User closer = findOrCreateUser("이진호", "010-9000-0003");
		User opener = findOrCreateUser("박서연", "010-9000-0004");
		User applicant = findOrCreateUser("최유진", "010-9000-0005");

		Store store = storeRepository.save(Store.create(
				owner,
				"배스킨라빈스 성수점",
				"아이스크림·디저트",
				"서울 성동구 연무장길",
				uniqueInviteCode()
		));

		activate(store, manager, MemberRole.MANAGER);
		activate(store, closer, MemberRole.WORKER);
		activate(store, opener, MemberRole.WORKER);
		activate(store, applicant, MemberRole.WORKER);

		shiftTemplateRepository.saveAll(List.of(
				ShiftTemplate.create(store, "오픈", LocalTime.of(10, 0), LocalTime.of(15, 0), 1),
				ShiftTemplate.create(store, "미들", LocalTime.of(14, 0), LocalTime.of(19, 0), 1),
				ShiftTemplate.create(store, "마감", LocalTime.of(18, 0), LocalTime.of(22, 0), 1)
		));

		seedAvailabilities(manager, closer, opener, applicant);
		LocalDate scheduleStart = LocalDate.now(SEOUL).minusDays(7);
		seedNotices(store, owner, scheduleStart);
		seedScheduleAndRequests(store, owner, manager, closer, opener, applicant, scheduleStart);

		log.info("Demo data initialized for store {}", store.getName());
	}

	private User findOrCreateUser(String name, String phone) {
		return userRepository.findByPhone(phone)
				.orElseGet(() -> userRepository.save(User.create(
						phone,
						passwordEncoder.encode(demoPassword),
						name
				)));
	}

	private String uniqueInviteCode() {
		for (int attempt = 0; attempt < 20; attempt++) {
			String candidate = inviteCodeGenerator.generate();
			if (!storeRepository.existsByInviteCode(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("데모 매장 초대코드를 발급하지 못했어요.");
	}

	private void activate(Store store, User user, MemberRole role) {
		StoreMember membership = StoreMember.request(store, user);
		membership.decide(MemberStatus.ACTIVE);
		if (role == MemberRole.MANAGER) {
			membership.changeRole(role);
		}
		storeMemberRepository.save(membership);
	}

	private void seedAvailabilities(User manager, User closer, User opener, User applicant) {
		availabilityRepository.saveAll(List.of(
				Availability.create(manager, 1, LocalTime.of(13, 0), LocalTime.of(20, 0)),
				Availability.create(manager, 3, LocalTime.of(13, 0), LocalTime.of(20, 0)),
				Availability.create(manager, 5, LocalTime.of(13, 0), LocalTime.of(20, 0)),
				Availability.create(closer, 2, LocalTime.of(17, 0), LocalTime.of(23, 0)),
				Availability.create(closer, 4, LocalTime.of(17, 0), LocalTime.of(23, 0)),
				Availability.create(closer, 6, LocalTime.of(17, 0), LocalTime.of(23, 0)),
				Availability.create(opener, 1, LocalTime.of(9, 0), LocalTime.of(16, 0)),
				Availability.create(opener, 3, LocalTime.of(9, 0), LocalTime.of(16, 0)),
				Availability.create(opener, 5, LocalTime.of(9, 0), LocalTime.of(16, 0)),
				Availability.create(applicant, 1, LocalTime.of(10, 0), LocalTime.of(22, 0)),
				Availability.create(applicant, 2, LocalTime.of(10, 0), LocalTime.of(22, 0)),
				Availability.create(applicant, 4, LocalTime.of(10, 0), LocalTime.of(22, 0))
		));
	}

	private void seedNotices(Store store, User owner, LocalDate scheduleStart) {
		storeNoticeRepository.saveAll(List.of(
				StoreNotice.create(store, scheduleStart.plusDays(7), "오픈 전 재고 점검 후 교대해 주세요.", owner),
				StoreNotice.create(store, scheduleStart.plusDays(12), "주말 행사 예정으로 마감 인원을 확인해 주세요.", owner)
		));
	}

	private void seedScheduleAndRequests(
			Store store,
			User owner,
			User manager,
			User closer,
			User opener,
			User applicant,
			LocalDate scheduleStart
	) {
		Shift coverShift = null;
		Shift exchangeShift = null;
		Shift offeredShift = null;
		for (int day = 0; day < 21; day++) {
			LocalDate date = scheduleStart.plusDays(day);
			Shift open = shiftRepository.save(shift(store, opener, owner, date, 10, 15, "오픈"));
			Shift middle = shiftRepository.save(shift(store, manager, owner, date, 14, 19, "미들"));
			Shift close = shiftRepository.save(shift(store, closer, owner, date, 18, 22, "마감"));
			if (day == 9) {
				coverShift = middle;
			}
			if (day == 10) {
				exchangeShift = open;
			}
			if (day == 11) {
				offeredShift = close;
			}
		}

		Shift openShift = shiftRepository.save(shift(
				store, null, owner, scheduleStart.plusDays(12), 12, 17, "미들"));

		ShiftRequest coverRequest = shiftRequestRepository.save(ShiftRequest.create(
				RequestType.COVER,
				RequestMode.PUBLIC,
				RequestScope.STORE,
				coverShift,
				manager,
				"개인 사정"
		));
		requestApplicationRepository.save(RequestApplication.applied(
				coverRequest, applicant, "제가 대신 근무할게요."
		));

		ShiftRequest exchangeRequest = shiftRequestRepository.save(ShiftRequest.create(
				RequestType.EXCHANGE,
				RequestMode.PUBLIC,
				RequestScope.STORE,
				exchangeShift,
				opener,
				"학교 일정"
		));
		requestAvailableDateRepository.saveAll(List.of(
				RequestAvailableDate.create(exchangeRequest, scheduleStart.plusDays(11)),
				RequestAvailableDate.create(exchangeRequest, scheduleStart.plusDays(12)),
				RequestAvailableDate.create(exchangeRequest, scheduleStart.plusDays(13))
		));
		RequestApplication exchangeApplication = requestApplicationRepository.save(
				RequestApplication.applied(exchangeRequest, closer, "수요일이나 모레 가능해요."));
		applicationOfferShiftRepository.save(ApplicationOfferShift.create(exchangeApplication, offeredShift));

		ShiftRequest urgentRequest = shiftRequestRepository.save(ShiftRequest.create(
				RequestType.OPEN_SHIFT,
				RequestMode.PUBLIC,
				RequestScope.STORE,
				openShift,
				owner,
				"주말 행사 추가 인력 모집"
		));

		notificationRepository.saveAll(List.of(
				Notification.create(manager, NotificationType.APPLICATION_NEW, coverRequest, store,
						"최유진님이 대타 요청에 지원했어요."),
				Notification.create(opener, NotificationType.APPLICATION_NEW, exchangeRequest, store,
						"이진호님이 교대 요청에 지원했어요."),
				Notification.create(closer, NotificationType.OPEN_SHIFT_POSTED, urgentRequest, store,
						"성수점에 새로운 급구 요청이 올라왔어요."),
				Notification.create(applicant, NotificationType.REQUEST_OPENED, coverRequest, store,
						"성수점에 새로운 대타 요청이 올라왔어요.")
		));
	}

	private Shift shift(
			Store store,
			User worker,
			User owner,
			LocalDate date,
			int startHour,
			int endHour,
			String position
	) {
		return Shift.create(
				store,
				worker,
				LocalDateTime.of(date, LocalTime.of(startHour, 0)),
				LocalDateTime.of(date, LocalTime.of(endHour, 0)),
				position,
				owner
		);
	}
}
