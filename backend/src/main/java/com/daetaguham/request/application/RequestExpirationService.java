package com.daetaguham.request.application;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import com.daetaguham.notification.application.NotificationService;
import com.daetaguham.request.domain.ApplicationStatus;
import com.daetaguham.request.domain.RequestApplication;
import com.daetaguham.request.domain.RequestApplicationRepository;
import com.daetaguham.request.domain.RequestStatus;
import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.request.domain.ShiftRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestExpirationService {

	private static final java.util.Set<RequestStatus> EXPIRABLE_STATUSES =
			EnumSet.of(RequestStatus.OPEN, RequestStatus.PENDING_APPROVAL);
	private static final java.util.Set<ApplicationStatus> NOTIFIABLE_APPLICATION_STATUSES =
			EnumSet.of(ApplicationStatus.PROPOSED, ApplicationStatus.APPLIED, ApplicationStatus.SELECTED);

	private final ShiftRequestRepository requestRepository;
	private final RequestApplicationRepository applicationRepository;
	private final NotificationService notificationService;

	public RequestExpirationService(
			ShiftRequestRepository requestRepository,
			RequestApplicationRepository applicationRepository,
			NotificationService notificationService
	) {
		this.requestRepository = requestRepository;
		this.applicationRepository = applicationRepository;
		this.notificationService = notificationService;
	}

	@Transactional
	public int expireDueRequests(LocalDateTime cutoff) {
		List<ShiftRequest> dueRequests = requestRepository
				.findDueForExpiration(EXPIRABLE_STATUSES, cutoff);
		for (ShiftRequest request : dueRequests) {
			List<RequestApplication> applications = applicationRepository
					.findAllByRequest_IdOrderByCreatedAtAsc(request.getId());
			request.expire();
			notificationService.notifyExpired(
					request,
					applications.stream()
							.filter(application -> NOTIFIABLE_APPLICATION_STATUSES.contains(
									application.getStatus()))
							.map(RequestApplication::getApplicant)
							.toList()
			);
		}
		return dueRequests.size();
	}
}
