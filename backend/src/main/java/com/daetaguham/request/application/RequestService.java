package com.daetaguham.request.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.daetaguham.request.domain.RequestAvailableDate;
import com.daetaguham.request.domain.RequestAvailableDateRepository;
import com.daetaguham.request.domain.RequestMode;
import com.daetaguham.request.domain.RequestScope;
import com.daetaguham.request.domain.RequestStatus;
import com.daetaguham.request.domain.RequestType;
import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.request.domain.ShiftRequestRepository;
import com.daetaguham.shift.application.ShiftNotFoundException;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.store.application.OwnerPermissionRequiredException;
import com.daetaguham.store.application.StoreNotFoundException;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.application.InvalidCredentialsException;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RequestService {

	private static final Set<RequestStatus> ACTIVE_STATUSES =
			EnumSet.of(RequestStatus.OPEN, RequestStatus.PENDING_APPROVAL);

	private final ShiftRequestRepository requestRepository;
	private final RequestAvailableDateRepository availableDateRepository;
	private final ShiftRepository shiftRepository;
	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final UserRepository userRepository;

	public RequestService(
			ShiftRequestRepository requestRepository,
			RequestAvailableDateRepository availableDateRepository,
			ShiftRepository shiftRepository,
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			UserRepository userRepository
	) {
		this.requestRepository = requestRepository;
		this.availableDateRepository = availableDateRepository;
		this.shiftRepository = shiftRepository;
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public RequestView createPublic(
			Long actorId,
			RequestType type,
			Long shiftId,
			RequestMode mode,
			RequestScope scope,
			Long targetUserId,
			Collection<Long> targetShiftIds,
			Collection<LocalDate> availableDates,
			String reason
	) {
		if (type == null || type == RequestType.OPEN_SHIFT) {
			throw new InvalidShiftRequestException("대타 또는 교대 요청만 등록할 수 있어요.");
		}
		RequestMode resolvedMode = mode == null ? RequestMode.PUBLIC : mode;
		if (resolvedMode == RequestMode.DIRECT) {
			throw new InvalidShiftRequestException("직접 제안은 지원 기능에서 등록해 주세요.");
		}
		if (targetUserId != null || (targetShiftIds != null && !targetShiftIds.isEmpty())) {
			throw new InvalidShiftRequestException("공개 요청에는 특정 직원이나 근무를 지정하지 않아요.");
		}

		Shift shift = shiftRepository.findById(shiftId).orElseThrow(ShiftNotFoundException::new);
		if (shift.getWorker() == null || !shift.getWorker().getId().equals(actorId)) {
			throw new ShiftRequestForbiddenException();
		}
		Store store = shift.getStore();
		if (!storeMemberRepository.existsByUser_IdAndStatusAndStore_Owner_Id(
				actorId, MemberStatus.ACTIVE, store.getOwner().getId())) {
			throw new ShiftRequestForbiddenException();
		}
		if (!shift.getStartAt().isAfter(LocalDateTime.now())) {
			throw new InvalidShiftRequestException("앞으로 예정된 근무만 요청할 수 있어요.");
		}
		ensureNoActiveRequest(shift.getId());

		RequestScope resolvedScope = scope == null ? RequestScope.STORE : scope;
		List<LocalDate> normalizedDates = validateRequestDetails(
				type, resolvedScope, availableDates, actorId, shift);
		String normalizedReason = normalizeText(reason, 100, "요청 사유는 100자 이내로 입력해 주세요.");

		ShiftRequest request = requestRepository.save(ShiftRequest.create(
				type, resolvedMode, resolvedScope, shift, shift.getWorker(), normalizedReason));
		availableDateRepository.saveAll(normalizedDates.stream()
				.map(date -> RequestAvailableDate.create(request, date))
				.toList());
		return toView(request, normalizedDates);
	}

	@Transactional
	public OpenShiftResult createOpenShift(
			Long actorId,
			Long storeId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String position,
			RequestScope scope,
			String message,
			Collection<Long> notifyUserIds
	) {
		Store store = storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
		if (!store.getOwner().getId().equals(actorId)) {
			throw new OwnerPermissionRequiredException();
		}
		if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
			throw new InvalidShiftRequestException("급구 근무의 시작·종료 시간을 확인해 주세요.");
		}
		if (!startAt.isAfter(LocalDateTime.now())) {
			throw new InvalidShiftRequestException("앞으로 예정된 근무만 급구로 등록할 수 있어요.");
		}
		String normalizedPosition = normalizeRequired(position, 10, "시간대는 10자 이내로 입력해 주세요.");
		String normalizedMessage = normalizeText(message, 100, "요청 메시지는 100자 이내로 입력해 주세요.");
		if (notifyUserIds == null || notifyUserIds.isEmpty()) {
			throw new InvalidShiftRequestException("알림을 보낼 직원을 한 명 이상 선택해 주세요.");
		}
		validateNotificationTargets(store, scope, notifyUserIds);

		User owner = userRepository.findById(actorId).orElseThrow(InvalidCredentialsException::new);
		Shift shift = shiftRepository.save(Shift.create(
				store, null, startAt, endAt, normalizedPosition, owner));
		ShiftRequest request = requestRepository.save(ShiftRequest.create(
				RequestType.OPEN_SHIFT,
				RequestMode.PUBLIC,
				scope == null ? RequestScope.STORE : scope,
				shift,
				owner,
				normalizedMessage
		));
		return new OpenShiftResult(toView(request, List.of()), 0);
	}

	@Transactional(readOnly = true)
	public RequestView find(Long actorId, Long requestId) {
		ShiftRequest request = findDetailed(requestId);
		requireVisible(actorId, request);
		return toView(request, findDates(request.getId()));
	}

	@Transactional
	public RequestView cancel(Long actorId, Long requestId) {
		ShiftRequest request = findDetailed(requestId);
		if (!request.getRequester().getId().equals(actorId)) {
			throw new ShiftRequestForbiddenException();
		}
		try {
			request.cancel();
		} catch (IllegalStateException exception) {
			throw new InvalidRequestStateException(exception.getMessage());
		}
		return toView(request, findDates(request.getId()));
	}

	private List<LocalDate> validateRequestDetails(
			RequestType type,
			RequestScope scope,
			Collection<LocalDate> availableDates,
			Long actorId,
			Shift shift
	) {
		if (type == RequestType.COVER) {
			if (availableDates != null && !availableDates.isEmpty()) {
				throw new InvalidShiftRequestException("대타 요청에는 교대 가능 날짜를 입력하지 않아요.");
			}
			return List.of();
		}
		if (scope != RequestScope.STORE) {
			throw new InvalidShiftRequestException("교대 요청은 같은 매장 안에서만 등록할 수 있어요.");
		}
		if (availableDates == null || availableDates.isEmpty()) {
			throw new InvalidShiftRequestException("교대 가능한 날짜를 한 개 이상 선택해 주세요.");
		}
		if (availableDates.stream().anyMatch(java.util.Objects::isNull)) {
			throw new InvalidShiftRequestException("교대 가능 날짜를 확인해 주세요.");
		}
		List<LocalDate> dates = new LinkedHashSet<>(availableDates).stream().sorted().toList();
		for (LocalDate date : dates) {
			if (date == null || date.isBefore(LocalDate.now())) {
				throw new InvalidShiftRequestException("오늘 이전 날짜는 교대 가능일로 선택할 수 없어요.");
			}
			if (date.equals(shift.getStartAt().toLocalDate())) {
				throw new InvalidShiftRequestException("원래 근무일은 교대 가능일에서 제외해 주세요.");
			}
			if (!shiftRepository.findAllByWorker_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
					actorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).isEmpty()) {
				throw new InvalidShiftRequestException("이미 근무가 있는 날은 교대 가능일로 선택할 수 없어요.");
			}
		}
		return dates;
	}

	private void validateNotificationTargets(Store store, RequestScope scope, Collection<Long> targetIds) {
		RequestScope resolvedScope = scope == null ? RequestScope.STORE : scope;
		for (Long targetId : new LinkedHashSet<>(targetIds)) {
			boolean eligible = resolvedScope == RequestScope.STORE
					? storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
							store.getId(), targetId, MemberStatus.ACTIVE)
					: storeMemberRepository.existsByUser_IdAndStatusAndStore_Owner_Id(
							targetId, MemberStatus.ACTIVE, store.getOwner().getId());
			if (!eligible) {
				throw new InvalidShiftRequestException("선택한 알림 대상 중 근무 가능한 직원이 아닌 사용자가 있어요.");
			}
		}
	}

	private void requireVisible(Long actorId, ShiftRequest request) {
		Store store = request.getShift().getStore();
		if (request.getRequester().getId().equals(actorId) || store.getOwner().getId().equals(actorId)) {
			return;
		}
		boolean visible = request.getScope() == RequestScope.STORE
				? storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
						store.getId(), actorId, MemberStatus.ACTIVE)
				: storeMemberRepository.existsByUser_IdAndStatusAndStore_Owner_Id(
						actorId, MemberStatus.ACTIVE, store.getOwner().getId());
		if (!visible) {
			throw new ShiftRequestForbiddenException();
		}
	}

	private void ensureNoActiveRequest(Long shiftId) {
		if (requestRepository.existsByShift_IdAndStatusIn(shiftId, ACTIVE_STATUSES)) {
			throw new ActiveShiftRequestExistsException();
		}
	}

	private ShiftRequest findDetailed(Long requestId) {
		return requestRepository.findDetailedById(requestId).orElseThrow(ShiftRequestNotFoundException::new);
	}

	private List<LocalDate> findDates(Long requestId) {
		return availableDateRepository.findAllByRequest_IdOrderByAvailableDateAsc(requestId).stream()
				.map(RequestAvailableDate::getAvailableDate)
				.toList();
	}

	private RequestView toView(ShiftRequest request, List<LocalDate> dates) {
		Shift shift = request.getShift();
		// open-in-view=false에서도 컨트롤러가 응답을 안전하게 만들 수 있도록
		// 응답에 필요한 지연 연관을 트랜잭션 안에서 초기화한다.
		request.getRequester().getName();
		shift.getStore().getName();
		if (shift.getWorker() != null) {
			shift.getWorker().getName();
		}
		boolean helper = shift.getWorker() != null
				&& !storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
						shift.getStore().getId(), shift.getWorker().getId(), MemberStatus.ACTIVE);
		boolean approvalRequired = request.getType() != RequestType.OPEN_SHIFT
				&& shift.getStore().isApprovalRequired();
		return new RequestView(request, dates, helper, approvalRequired);
	}

	private String normalizeRequired(String value, int maximum, String message) {
		if (!StringUtils.hasText(value) || value.trim().length() > maximum) {
			throw new InvalidShiftRequestException(message);
		}
		return value.trim();
	}

	private String normalizeText(String value, int maximum, String message) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.length() > maximum) {
			throw new InvalidShiftRequestException(message);
		}
		return normalized;
	}

	public record RequestView(
			ShiftRequest request,
			List<LocalDate> availableDates,
			boolean helper,
			boolean approvalRequired
	) {
	}

	public record OpenShiftResult(RequestView request, int notifiedCount) {
	}
}
