package com.daetaguham.request.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.daetaguham.request.domain.ApplicationStatus;
import com.daetaguham.request.domain.RequestApplication;
import com.daetaguham.request.domain.RequestApplicationRepository;
import com.daetaguham.request.domain.RequestAvailableDate;
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
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestQueryService {

	private static final Set<ApplicationStatus> COUNTED_APPLICATION_STATUSES =
			EnumSet.of(ApplicationStatus.PROPOSED, ApplicationStatus.APPLIED, ApplicationStatus.SELECTED);
	private static final Set<ApplicationStatus> PROPOSAL_ONLY_STATUSES =
			EnumSet.of(ApplicationStatus.PROPOSED, ApplicationStatus.DECLINED);

	private final ShiftRequestRepository requestRepository;
	private final RequestApplicationRepository applicationRepository;
	private final RequestAvailableDateRepository availableDateRepository;
	private final ShiftRepository shiftRepository;
	private final StoreMemberRepository storeMemberRepository;

	public RequestQueryService(
			ShiftRequestRepository requestRepository,
			RequestApplicationRepository applicationRepository,
			RequestAvailableDateRepository availableDateRepository,
			ShiftRepository shiftRepository,
			StoreMemberRepository storeMemberRepository
	) {
		this.requestRepository = requestRepository;
		this.applicationRepository = applicationRepository;
		this.availableDateRepository = availableDateRepository;
		this.shiftRepository = shiftRepository;
		this.storeMemberRepository = storeMemberRepository;
	}

	@Transactional(readOnly = true)
	public RequestPage findBoard(
			Long actorId,
			RequestType type,
			Long storeId,
			int page,
			int size
	) {
		validatePage(page, size);
		List<StoreMember> memberships = storeMemberRepository
				.findAllByUser_IdAndStatus(actorId, MemberStatus.ACTIVE);
		Set<Long> storeIds = memberships.stream()
				.map(member -> member.getStore().getId())
				.collect(java.util.stream.Collectors.toSet());
		Set<Long> ownerIds = memberships.stream()
				.map(member -> member.getStore().getOwner().getId())
				.collect(java.util.stream.Collectors.toSet());
		LocalDateTime now = LocalDateTime.now();

		List<ShiftRequest> visible = requestRepository
				.findAllByModeAndStatusOrderByCreatedAtDescIdDesc(
						RequestMode.PUBLIC, RequestStatus.OPEN).stream()
				.filter(request -> !request.getRequester().getId().equals(actorId))
				.filter(request -> request.getShift().getStartAt().isAfter(now))
				.filter(request -> type == null || request.getType() == type)
				.filter(request -> storeId == null || request.getShift().getStore().getId().equals(storeId))
				.filter(request -> isInScope(request, storeIds, ownerIds))
				.filter(request -> hasNoTimeConflict(actorId, request.getShift()))
				.filter(request -> request.getType() != RequestType.EXCHANGE
						|| hasEligibleExchangeShift(actorId, request))
				.toList();

		int from = pageStart(page, size, visible.size());
		int to = Math.min(from + size, visible.size());
		List<RequestSummaryView> content = visible.subList(from, to).stream()
				.map(request -> toView(request, findMyStatus(request.getId(), actorId)))
				.toList();
		return new RequestPage(
				visible.size(), totalPages(visible.size(), size), page, size, content);
	}

	@Transactional(readOnly = true)
	public RequestPage findMine(Long actorId, String box, int page, int size) {
		validatePage(page, size);
		ActivityBox activityBox = ActivityBox.parse(box);
		PageRequest pageable = PageRequest.of(page - 1, size);
		if (activityBox == ActivityBox.SENT) {
			Page<ShiftRequest> result = requestRepository
					.findAllByRequester_IdOrderByCreatedAtDescIdDesc(actorId, pageable);
			return new RequestPage(
					result.getTotalElements(), result.getTotalPages(), page, size,
					result.getContent().stream().map(request -> toView(request, null)).toList());
		}

		Page<RequestApplication> result = activityBox == ActivityBox.APPLIED
				? applicationRepository.findPublicActivities(
						actorId, PROPOSAL_ONLY_STATUSES, pageable)
				: applicationRepository.findReceivedProposals(
						actorId, PROPOSAL_ONLY_STATUSES, pageable);
		return new RequestPage(
				result.getTotalElements(), result.getTotalPages(), page, size,
				result.getContent().stream()
						.map(application -> toView(application.getRequest(), application.getStatus()))
						.toList());
	}

	private boolean isInScope(ShiftRequest request, Set<Long> storeIds, Set<Long> ownerIds) {
		return request.getScope() == RequestScope.STORE
				? storeIds.contains(request.getShift().getStore().getId())
				: ownerIds.contains(request.getShift().getStore().getOwner().getId());
	}

	private boolean hasNoTimeConflict(Long actorId, Shift shift) {
		return shiftRepository.findOverlappingShifts(
				actorId, shift.getStartAt(), shift.getEndAt()).isEmpty();
	}

	private boolean hasEligibleExchangeShift(Long actorId, ShiftRequest request) {
		Long storeId = request.getShift().getStore().getId();
		for (LocalDate date : findDates(request.getId())) {
			boolean hasShift = shiftRepository
					.findAllByWorker_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
							actorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
					.anyMatch(shift -> shift.getStore().getId().equals(storeId));
			if (hasShift) {
				return true;
			}
		}
		return false;
	}

	private ApplicationStatus findMyStatus(Long requestId, Long actorId) {
		return applicationRepository.findByRequest_IdAndApplicant_Id(requestId, actorId)
				.map(RequestApplication::getStatus)
				.orElse(null);
	}

	private RequestSummaryView toView(ShiftRequest request, ApplicationStatus myStatus) {
		Shift shift = request.getShift();
		request.getRequester().getName();
		shift.getStore().getName();
		if (shift.getWorker() != null) {
			shift.getWorker().getName();
		}
		boolean helper = shift.getWorker() != null
				&& !storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
						shift.getStore().getId(), shift.getWorker().getId(), MemberStatus.ACTIVE);
		int applicantCount = Math.toIntExact(applicationRepository.countByRequest_IdAndStatusIn(
				request.getId(), COUNTED_APPLICATION_STATUSES));
		return new RequestSummaryView(
				request, findDates(request.getId()), helper, applicantCount, myStatus);
	}

	private List<LocalDate> findDates(Long requestId) {
		return availableDateRepository.findAllByRequest_IdOrderByAvailableDateAsc(requestId).stream()
				.map(RequestAvailableDate::getAvailableDate)
				.toList();
	}

	private void validatePage(int page, int size) {
		if (page < 1) {
			throw new InvalidRequestQueryException("페이지는 1 이상이어야 해요.");
		}
		if (size < 1 || size > 100) {
			throw new InvalidRequestQueryException("페이지 크기는 1~100 사이여야 해요.");
		}
	}

	private int pageStart(int page, int size, int total) {
		long start = (long) (page - 1) * size;
		return start >= total ? total : (int) start;
	}

	private int totalPages(int total, int size) {
		return total == 0 ? 0 : (int) Math.ceil((double) total / size);
	}

	private enum ActivityBox {
		SENT,
		APPLIED,
		RECEIVED;

		private static ActivityBox parse(String value) {
			if (value == null) {
				throw new InvalidRequestQueryException("box는 sent, applied, received 중 하나여야 해요.");
			}
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException exception) {
				throw new InvalidRequestQueryException(
						"box는 sent, applied, received 중 하나여야 해요.");
			}
		}
	}

	public record RequestSummaryView(
			ShiftRequest request,
			List<LocalDate> availableDates,
			boolean helper,
			int applicantCount,
			ApplicationStatus myApplicationStatus
	) {
	}

	public record RequestPage(
			long totalElements,
			int totalPages,
			int page,
			int size,
			List<RequestSummaryView> content
	) {
	}
}
