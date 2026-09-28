package com.daetaguham.request.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.daetaguham.notification.domain.Notification;
import com.daetaguham.notification.domain.NotificationRepository;
import com.daetaguham.notification.domain.NotificationType;
import com.daetaguham.request.domain.RequestApplication;
import com.daetaguham.request.domain.RequestApplicationRepository;
import com.daetaguham.request.domain.RequestMode;
import com.daetaguham.request.domain.RequestScope;
import com.daetaguham.request.domain.RequestStatus;
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
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:request-expiration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"app.requests.expiration.initial-delay=PT1H"
})
@Transactional
class RequestExpirationIntegrationTest {

	@Autowired private RequestExpirationService expirationService;
	@Autowired private ShiftRequestRepository requestRepository;
	@Autowired private RequestApplicationRepository applicationRepository;
	@Autowired private ShiftRepository shiftRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private StoreRepository storeRepository;
	@Autowired private StoreMemberRepository storeMemberRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private User owner;
	private User requester;
	private User candidate;
	private Store store;
	private LocalDateTime cutoff;

	@BeforeEach
	void setUp() {
		owner = saveUser("010-7111-1111", "김효주");
		requester = saveUser("010-7222-2222", "김민");
		candidate = saveUser("010-7333-3333", "이진호");
		store = storeRepository.save(Store.create(owner, "성수점", "디저트", null, "EXP001"));
		activate(requester);
		activate(candidate);
		cutoff = LocalDateTime.of(2026, 9, 28, 12, 0);
	}

	@Test
	void expiresOpenAndPendingRequestsOnceAndNotifiesParticipants() {
		ShiftRequest open = saveRequest(cutoff.minusMinutes(10), RequestStatus.OPEN);
		RequestApplication openApplication = applicationRepository.save(
				RequestApplication.applied(open, candidate, "지원합니다"));

		ShiftRequest pending = saveRequest(cutoff, RequestStatus.PENDING_APPROVAL);
		RequestApplication pendingApplication = RequestApplication.applied(
				pending, candidate, "지원합니다");
		pendingApplication.select(null);
		applicationRepository.save(pendingApplication);

		ShiftRequest future = saveRequest(cutoff.plusMinutes(1), RequestStatus.OPEN);
		ShiftRequest confirmed = saveRequest(cutoff.minusMinutes(20), RequestStatus.CONFIRMED);
		ShiftRequest canceled = saveRequest(cutoff.minusMinutes(30), RequestStatus.CANCELED);

		assertThat(expirationService.expireDueRequests(cutoff)).isEqualTo(2);
		assertThat(requestRepository.findById(open.getId()).orElseThrow().getStatus())
				.isEqualTo(RequestStatus.EXPIRED);
		assertThat(requestRepository.findById(pending.getId()).orElseThrow().getStatus())
				.isEqualTo(RequestStatus.EXPIRED);
		assertThat(requestRepository.findById(future.getId()).orElseThrow().getStatus())
				.isEqualTo(RequestStatus.OPEN);
		assertThat(requestRepository.findById(confirmed.getId()).orElseThrow().getStatus())
				.isEqualTo(RequestStatus.CONFIRMED);
		assertThat(requestRepository.findById(canceled.getId()).orElseThrow().getStatus())
				.isEqualTo(RequestStatus.CANCELED);
		assertThat(openApplication.getStatus()).isEqualTo(com.daetaguham.request.domain.ApplicationStatus.APPLIED);
		assertThat(pendingApplication.getStatus()).isEqualTo(com.daetaguham.request.domain.ApplicationStatus.SELECTED);

		List<Notification> requesterNotifications = notificationRepository
				.findAllByUser_IdOrderByCreatedAtDesc(requester.getId(), PageRequest.of(0, 10))
				.getContent();
		List<Notification> candidateNotifications = notificationRepository
				.findAllByUser_IdOrderByCreatedAtDesc(candidate.getId(), PageRequest.of(0, 10))
				.getContent();
		assertThat(requesterNotifications)
				.hasSize(2)
				.allMatch(notification -> notification.getType() == NotificationType.EXPIRED);
		assertThat(candidateNotifications)
				.hasSize(2)
				.allMatch(notification -> notification.getType() == NotificationType.EXPIRED);

		assertThat(expirationService.expireDueRequests(cutoff.plusMinutes(1))).isEqualTo(1);
		assertThat(notificationRepository.countByUser_IdAndIsReadFalse(requester.getId())).isEqualTo(3);
		assertThat(notificationRepository.countByUser_IdAndIsReadFalse(candidate.getId())).isEqualTo(2);
		assertThat(expirationService.expireDueRequests(cutoff.plusMinutes(1))).isZero();
		assertThat(notificationRepository.countByUser_IdAndIsReadFalse(requester.getId())).isEqualTo(3);
	}

	private ShiftRequest saveRequest(LocalDateTime startAt, RequestStatus targetStatus) {
		Shift shift = shiftRepository.save(Shift.create(
				store, requester, startAt, startAt.plusHours(6), "미들", owner));
		ShiftRequest request = ShiftRequest.create(
				RequestType.COVER,
				RequestMode.PUBLIC,
				RequestScope.STORE,
				shift,
				requester,
				"개인 사정"
		);
		if (targetStatus == RequestStatus.PENDING_APPROVAL) {
			request.markPendingApproval();
		} else if (targetStatus == RequestStatus.CONFIRMED) {
			request.confirm();
		} else if (targetStatus == RequestStatus.CANCELED) {
			request.cancel();
		}
		return requestRepository.save(request);
	}

	private void activate(User user) {
		StoreMember membership = StoreMember.request(store, user);
		membership.decide(MemberStatus.ACTIVE);
		storeMemberRepository.save(membership);
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, passwordEncoder.encode("password123"), name));
	}
}
