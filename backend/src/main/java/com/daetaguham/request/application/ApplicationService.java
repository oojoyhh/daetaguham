package com.daetaguham.request.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.daetaguham.notification.application.NotificationService;
import com.daetaguham.request.domain.ApplicationOfferShift;
import com.daetaguham.request.domain.ApplicationOfferShiftRepository;
import com.daetaguham.request.domain.ApplicationStatus;
import com.daetaguham.request.domain.RequestApplication;
import com.daetaguham.request.domain.RequestApplicationRepository;
import com.daetaguham.request.domain.RequestAvailableDateRepository;
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
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ApplicationService {

	private final ShiftRequestRepository requestRepository;
	private final RequestApplicationRepository applicationRepository;
	private final ApplicationOfferShiftRepository offerShiftRepository;
	private final RequestAvailableDateRepository availableDateRepository;
	private final ShiftRepository shiftRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;

	public ApplicationService(
			ShiftRequestRepository requestRepository,
			RequestApplicationRepository applicationRepository,
			ApplicationOfferShiftRepository offerShiftRepository,
			RequestAvailableDateRepository availableDateRepository,
			ShiftRepository shiftRepository,
			StoreMemberRepository storeMemberRepository,
			UserRepository userRepository,
			NotificationService notificationService
	) {
		this.requestRepository = requestRepository;
		this.applicationRepository = applicationRepository;
		this.offerShiftRepository = offerShiftRepository;
		this.availableDateRepository = availableDateRepository;
		this.shiftRepository = shiftRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.userRepository = userRepository;
		this.notificationService = notificationService;
	}

	@Transactional
	public RequestApplication createProposal(
			ShiftRequest request,
			Long targetUserId,
			Collection<Long> targetShiftIds
	) {
		if (targetUserId == null) {
			throw new InvalidShiftRequestException("제안 받을 직원을 선택해 주세요.");
		}
		User target = userRepository.findById(targetUserId)
				.orElseThrow(RequestApplicationNotFoundException::new);
		requireEligibleApplicant(target.getId(), request);
		if (request.getRequester().getId().equals(target.getId())) {
			throw new ShiftRequestForbiddenException();
		}
		if (applicationRepository.existsByRequest_IdAndStatus(request.getId(), ApplicationStatus.PROPOSED)) {
			throw new InvalidRequestStateException("먼저 현재 지정 제안의 응답을 기다려 주세요.");
		}
		if (applicationRepository.existsByRequest_IdAndApplicant_Id(request.getId(), target.getId())) {
			throw new InvalidRequestStateException("이미 이 직원에게 제안한 기록이 있어요.");
		}
		List<Shift> offeredShifts = validateOfferedShifts(request, target.getId(), targetShiftIds, true);
		RequestApplication application = applicationRepository.save(RequestApplication.proposed(request, target));
		saveOffers(application, offeredShifts);
		notificationService.notifyProposalReceived(request, target);
		return application;
	}

	@Transactional
	public ApplicationView apply(
			Long actorId,
			Long requestId,
			Collection<Long> offeredShiftIds,
			String message
	) {
		ShiftRequest request = findRequest(requestId);
		if (request.getMode() != RequestMode.PUBLIC || request.getStatus() != RequestStatus.OPEN) {
			throw new InvalidRequestStateException("현재 공개 모집 중인 요청에만 지원할 수 있어요.");
		}
		if (request.getRequester().getId().equals(actorId)) {
			throw new ShiftRequestForbiddenException();
		}
		requireEligibleApplicant(actorId, request);
		List<Shift> offeredShifts = validateOfferedShifts(request, actorId, offeredShiftIds, false);
		String normalizedMessage = normalizeMessage(message);
		User applicant = userRepository.findById(actorId).orElseThrow(RequestApplicationNotFoundException::new);

		RequestApplication application = applicationRepository
				.findByRequest_IdAndApplicant_Id(requestId, actorId)
				.map(existing -> {
					try {
						existing.reapply(normalizedMessage);
					} catch (IllegalStateException exception) {
						throw new InvalidRequestStateException("이미 이 요청에 지원했어요.");
					}
					offerShiftRepository.deleteAllByApplication_Id(existing.getId());
					offerShiftRepository.flush();
					return existing;
				})
				.orElseGet(() -> applicationRepository.save(
						RequestApplication.applied(request, applicant, normalizedMessage)));
		saveOffers(application, offeredShifts);
		notificationService.notifyApplicationNew(request, applicant);
		return toView(application);
	}

	@Transactional
	public ApplicationView withdraw(Long actorId, Long applicationId) {
		RequestApplication application = findApplication(applicationId);
		if (!application.getApplicant().getId().equals(actorId)) {
			throw new ShiftRequestForbiddenException();
		}
		try {
			application.withdraw();
		} catch (IllegalStateException exception) {
			throw new InvalidRequestStateException(exception.getMessage());
		}
		return toView(application);
	}

	@Transactional
	public Long respondToProposal(
			Long actorId,
			Long applicationId,
			boolean accept,
			Long offerShiftId
	) {
		RequestApplication application = findApplication(applicationId);
		if (!application.getApplicant().getId().equals(actorId)) {
			throw new ShiftRequestForbiddenException();
		}
		ShiftRequest request = application.getRequest();
		if (request.getStatus() != RequestStatus.OPEN) {
			throw new InvalidRequestStateException("모집 중인 요청의 제안만 응답할 수 있어요.");
		}
		try {
			if (!accept) {
				application.decline();
				notificationService.notifyProposalDeclined(request, application.getApplicant());
				return request.getId();
			}
			Shift selected = validateProposalAcceptance(application, offerShiftId);
			application.select(selected);
			request.markPendingApproval();
			notificationService.notifyApprovalNeeded(request);
			return request.getId();
		} catch (IllegalStateException exception) {
			throw new InvalidRequestStateException(exception.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public List<ApplicationView> findAllViews(Long requestId) {
		return applicationRepository.findAllByRequest_IdOrderByCreatedAtAsc(requestId).stream()
				.map(this::toView)
				.toList();
	}

	@Transactional(readOnly = true)
	public ApplicationView findMyView(Long requestId, Long actorId) {
		return applicationRepository.findByRequest_IdAndApplicant_Id(requestId, actorId)
				.map(this::toView)
				.orElse(null);
	}

	@Transactional(readOnly = true)
	public ApplicationView findView(Long applicationId) {
		return toView(findApplication(applicationId));
	}

	@Transactional(readOnly = true)
	public boolean hasActiveProposal(Long requestId) {
		return applicationRepository.existsByRequest_IdAndStatus(requestId, ApplicationStatus.PROPOSED);
	}

	private List<Shift> validateOfferedShifts(
			ShiftRequest request,
			Long applicantId,
			Collection<Long> offeredShiftIds,
			boolean direct
	) {
		if (request.getType() != RequestType.EXCHANGE) {
			if (offeredShiftIds != null && !offeredShiftIds.isEmpty()) {
				throw new InvalidShiftRequestException("대타·급구 지원에는 교대 근무를 선택하지 않아요.");
			}
			ensureNoOverlap(applicantId, request.getShift(), -1L);
			return List.of();
		}
		if (offeredShiftIds == null || offeredShiftIds.isEmpty()) {
			throw new InvalidShiftRequestException("교대할 상대 근무를 한 개 이상 선택해 주세요.");
		}
		Set<Long> ids = new LinkedHashSet<>(offeredShiftIds);
		if (ids.contains(null)) {
			throw new InvalidShiftRequestException("교대할 근무를 확인해 주세요.");
		}
		List<Shift> shifts = shiftRepository.findAllById(ids);
		if (shifts.size() != ids.size()) {
			throw new InvalidShiftRequestException("존재하지 않는 교대 근무가 포함되어 있어요.");
		}
		Set<LocalDate> allowedDates = direct ? Set.of() : availableDateRepository
				.findAllByRequest_IdOrderByAvailableDateAsc(request.getId()).stream()
				.map(item -> item.getAvailableDate())
				.collect(java.util.stream.Collectors.toSet());
		for (Shift shift : shifts) {
			if (shift.getWorker() == null || !shift.getWorker().getId().equals(applicantId)
					|| !shift.getStore().getId().equals(request.getShift().getStore().getId())) {
				throw new InvalidShiftRequestException("같은 매장에 있는 본인 근무만 교대 후보로 선택할 수 있어요.");
			}
			if (!shift.getStartAt().isAfter(LocalDateTime.now())) {
				throw new InvalidShiftRequestException("앞으로 예정된 근무만 교대 후보로 선택할 수 있어요.");
			}
			if (!direct && !allowedDates.contains(shift.getStartAt().toLocalDate())) {
				throw new InvalidShiftRequestException("요청자가 제시한 가능 날짜의 근무만 선택할 수 있어요.");
			}
		}
		if (!direct) {
			ensureNoOverlap(applicantId, request.getShift(), -1L);
		}
		return shifts.stream().sorted(java.util.Comparator.comparing(Shift::getStartAt)).toList();
	}

	private Shift validateProposalAcceptance(RequestApplication application, Long offerShiftId) {
		ShiftRequest request = application.getRequest();
		if (request.getType() != RequestType.EXCHANGE) {
			if (offerShiftId != null) {
				throw new InvalidShiftRequestException("대타 제안에는 교대 근무를 선택하지 않아요.");
			}
			ensureNoOverlap(application.getApplicant().getId(), request.getShift(), -1L);
			return null;
		}
		if (offerShiftId == null) {
			throw new InvalidShiftRequestException("실제로 바꿀 근무를 선택해 주세요.");
		}
		Shift offered = offerShiftRepository.findAllByApplication_IdOrderByShift_StartAtAsc(application.getId())
				.stream()
				.map(ApplicationOfferShift::getShift)
				.filter(shift -> shift.getId().equals(offerShiftId))
				.findFirst()
				.orElseThrow(() -> new InvalidShiftRequestException("제안에 포함된 근무만 선택할 수 있어요."));
		ensureNoOverlap(application.getApplicant().getId(), request.getShift(), offered.getId());
		ensureNoOverlap(request.getRequester().getId(), offered, request.getShift().getId());
		return offered;
	}

	private void ensureNoOverlap(Long workerId, Shift target, Long excludeId) {
		if (shiftRepository.existsOverlappingShift(
				workerId, target.getStartAt(), target.getEndAt(), excludeId)) {
			throw new InvalidRequestStateException("같은 시간에 이미 배정된 근무가 있어요.");
		}
	}

	private void requireEligibleApplicant(Long userId, ShiftRequest request) {
		Store store = request.getShift().getStore();
		boolean eligible = request.getScope() == RequestScope.STORE
				? storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
						store.getId(), userId, MemberStatus.ACTIVE)
				: storeMemberRepository.existsByUser_IdAndStatusAndStore_Owner_Id(
						userId, MemberStatus.ACTIVE, store.getOwner().getId());
		if (!eligible || store.getOwner().getId().equals(userId)) {
			throw new ShiftRequestForbiddenException();
		}
	}

	private void saveOffers(RequestApplication application, List<Shift> shifts) {
		offerShiftRepository.saveAll(shifts.stream()
				.map(shift -> ApplicationOfferShift.create(application, shift))
				.toList());
	}

	private ShiftRequest findRequest(Long requestId) {
		return requestRepository.findDetailedById(requestId).orElseThrow(ShiftRequestNotFoundException::new);
	}

	private RequestApplication findApplication(Long applicationId) {
		return applicationRepository.findDetailedById(applicationId)
				.orElseThrow(RequestApplicationNotFoundException::new);
	}

	private String normalizeMessage(String message) {
		if (!StringUtils.hasText(message)) {
			return null;
		}
		String normalized = message.trim();
		if (normalized.length() > 60) {
			throw new InvalidShiftRequestException("지원 메시지는 60자 이내로 입력해 주세요.");
		}
		return normalized;
	}

	private ApplicationView toView(RequestApplication application) {
		User applicant = application.getApplicant();
		applicant.getName();
		List<Shift> offers = offerShiftRepository
				.findAllByApplication_IdOrderByShift_StartAtAsc(application.getId()).stream()
				.map(ApplicationOfferShift::getShift)
				.toList();
		String storeName = applicantStoreName(application.getRequest(), applicant.getId());
		double weekHours = calculateWeekHours(applicant.getId());
		if (application.getSelectedOfferShift() != null) {
			application.getSelectedOfferShift().getStore().getName();
			if (application.getSelectedOfferShift().getWorker() != null) {
				application.getSelectedOfferShift().getWorker().getName();
			}
		}
		return new ApplicationView(application, offers, storeName, 0, weekHours, 0, 0);
	}

	private String applicantStoreName(ShiftRequest request, Long applicantId) {
		List<StoreMember> memberships = storeMemberRepository
				.findAllByUser_IdAndStatus(applicantId, MemberStatus.ACTIVE);
		return memberships.stream()
				.filter(member -> member.getStore().getId().equals(request.getShift().getStore().getId()))
				.map(member -> member.getStore().getName())
				.findFirst()
				.orElseGet(() -> memberships.stream()
						.filter(member -> member.getStore().getOwner().getId()
								.equals(request.getShift().getStore().getOwner().getId()))
						.sorted(java.util.Comparator.comparing(member -> member.getStore().getId()))
						.map(member -> member.getStore().getName())
						.findFirst()
						.orElse(null));
	}

	private double calculateWeekHours(Long applicantId) {
		LocalDate today = LocalDate.now();
		LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return shiftRepository
				.findAllByWorker_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
						applicantId, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay())
				.stream()
				.mapToLong(shift -> Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes())
				.sum() / 60.0;
	}

	public record ApplicationView(
			RequestApplication application,
			List<Shift> offeredShifts,
			String applicantStoreName,
			int helperCount,
			double weekHours,
			int gaveToMe,
			int gotFromMe
	) {
	}
}
