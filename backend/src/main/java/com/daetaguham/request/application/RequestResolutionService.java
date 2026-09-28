package com.daetaguham.request.application;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import com.daetaguham.request.domain.ApplicationOfferShift;
import com.daetaguham.request.domain.ApplicationOfferShiftRepository;
import com.daetaguham.request.domain.ApplicationStatus;
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
import com.daetaguham.store.application.StoreManagementForbiddenException;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.store.application.StoreNotFoundException;
import com.daetaguham.user.application.InvalidCredentialsException;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RequestResolutionService {

	private static final java.util.Set<RequestStatus> ACTIVE_REQUEST_STATUSES =
			EnumSet.of(RequestStatus.OPEN, RequestStatus.PENDING_APPROVAL);

	private final ShiftRequestRepository requestRepository;
	private final RequestApplicationRepository applicationRepository;
	private final ApplicationOfferShiftRepository offerShiftRepository;
	private final ShiftRepository shiftRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final UserRepository userRepository;
	private final StoreRepository storeRepository;

	public RequestResolutionService(
			ShiftRequestRepository requestRepository,
			RequestApplicationRepository applicationRepository,
			ApplicationOfferShiftRepository offerShiftRepository,
			ShiftRepository shiftRepository,
			StoreMemberRepository storeMemberRepository,
			UserRepository userRepository,
			StoreRepository storeRepository
	) {
		this.requestRepository = requestRepository;
		this.applicationRepository = applicationRepository;
		this.offerShiftRepository = offerShiftRepository;
		this.shiftRepository = shiftRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.userRepository = userRepository;
		this.storeRepository = storeRepository;
	}

	@Transactional
	public Long select(
			Long actorId,
			Long requestId,
			Long applicationId,
			Long offerShiftId
	) {
		ShiftRequest request = findRequest(requestId);
		if (!request.getRequester().getId().equals(actorId)) {
			throw new ShiftRequestForbiddenException();
		}
		if (request.getMode() != RequestMode.PUBLIC || request.getStatus() != RequestStatus.OPEN) {
			throw new InvalidRequestStateException("현재 공개 모집 중인 요청만 지원자를 선택할 수 있어요.");
		}
		RequestApplication application = findApplication(applicationId);
		if (!application.getRequest().getId().equals(requestId)
				|| application.getStatus() != ApplicationStatus.APPLIED) {
			throw new InvalidRequestStateException("이 요청에서 선택할 수 있는 지원이 아니에요.");
		}
		Shift selectedOffer = resolveOfferShift(request, application, offerShiftId);
		application.select(selectedOffer);

		if (request.getType() == RequestType.OPEN_SHIFT || !request.getShift().getStore().isApprovalRequired()) {
			confirm(request, application);
		} else {
			request.markPendingApproval();
		}
		return requestId;
	}

	@Transactional
	public Long decideApproval(
			Long actorId,
			Long requestId,
			ApprovalDecision decision,
			String comment
	) {
		ShiftRequest request = findRequest(requestId);
		requireStoreManagement(actorId, request.getShift().getStore());
		if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
			throw new InvalidRequestStateException("승인 대기 중인 요청만 처리할 수 있어요.");
		}
		RequestApplication selected = applicationRepository
				.findByRequest_IdAndStatus(requestId, ApplicationStatus.SELECTED)
				.orElseThrow(RequestApplicationNotFoundException::new);
		User decider = userRepository.findById(actorId).orElseThrow(InvalidCredentialsException::new);

		if (decision == ApprovalDecision.REJECT) {
			String normalizedComment = normalizeRejectComment(comment);
			selected.reject(decider, normalizedComment);
			request.reopenAfterRejection();
			return requestId;
		}
		if (decision != ApprovalDecision.APPROVE) {
			throw new InvalidShiftRequestException("승인 또는 반려를 선택해 주세요.");
		}
		selected.approveBy(decider);
		confirm(request, selected);
		return requestId;
	}

	@Transactional(readOnly = true)
	public List<PendingApproval> findPendingApprovals(Long actorId, Long storeId) {
		Store store = storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
		requireStoreManagement(actorId, store);
		List<ShiftRequest> requests = requestRepository
				.findAllByShift_Store_IdAndStatusOrderByCreatedAtAsc(
						storeId, RequestStatus.PENDING_APPROVAL);
		if (!requests.isEmpty()) {
			return requests.stream()
					.map(request -> new PendingApproval(
							request,
							applicationRepository.findByRequest_IdAndStatus(
									request.getId(), ApplicationStatus.SELECTED)
									.orElseThrow(RequestApplicationNotFoundException::new)))
					.toList();
		}
		return List.of();
	}

	private void confirm(ShiftRequest request, RequestApplication selected) {
		Shift original = request.getShift();
		User applicant = selected.getApplicant();
		requireEligibleApplicant(applicant.getId(), request);
		if (!original.getStartAt().isAfter(LocalDateTime.now())) {
			throw new InvalidRequestStateException("이미 시작한 근무 요청은 확정할 수 없어요.");
		}

		if (request.getType() == RequestType.EXCHANGE) {
			Shift offered = selected.getSelectedOfferShift();
			if (offered == null || offered.getWorker() == null
					|| !offered.getWorker().getId().equals(applicant.getId())
					|| original.getWorker() == null
					|| !original.getWorker().getId().equals(request.getRequester().getId())) {
				throw new InvalidRequestStateException("교대 근무의 현재 담당자가 달라져 확정할 수 없어요.");
			}
			ensureNoOverlap(applicant.getId(), original, offered.getId());
			ensureNoOverlap(request.getRequester().getId(), offered, original.getId());
			cancelOtherRequests(offered.getId(), request.getId());
			original.assignWorker(applicant);
			offered.assignWorker(request.getRequester());
		} else {
			if (request.getType() == RequestType.COVER
					&& (original.getWorker() == null
					|| !original.getWorker().getId().equals(request.getRequester().getId()))) {
				throw new InvalidRequestStateException("대타 근무의 현재 담당자가 달라져 확정할 수 없어요.");
			}
			if (request.getType() == RequestType.OPEN_SHIFT && original.getWorker() != null) {
				throw new InvalidRequestStateException("급구 근무에 이미 담당자가 정해졌어요.");
			}
			ensureNoOverlap(applicant.getId(), original, original.getId());
			original.assignWorker(applicant);
		}

		request.confirm();
		for (RequestApplication application :
				applicationRepository.findAllByRequest_IdOrderByCreatedAtAsc(request.getId())) {
			if (!application.getId().equals(selected.getId())) {
				application.markNotSelected();
			}
		}
	}

	private Shift resolveOfferShift(
			ShiftRequest request,
			RequestApplication application,
			Long offerShiftId
	) {
		if (request.getType() != RequestType.EXCHANGE) {
			if (offerShiftId != null) {
				throw new InvalidShiftRequestException("대타·급구 지원에는 교대 근무를 선택하지 않아요.");
			}
			return null;
		}
		if (offerShiftId == null) {
			throw new InvalidShiftRequestException("실제로 바꿀 근무를 선택해 주세요.");
		}
		return offerShiftRepository.findAllByApplication_IdOrderByShift_StartAtAsc(application.getId())
				.stream()
				.map(ApplicationOfferShift::getShift)
				.filter(shift -> shift.getId().equals(offerShiftId))
				.findFirst()
				.orElseThrow(() -> new InvalidShiftRequestException("지원자가 제안한 근무만 선택할 수 있어요."));
	}

	private void cancelOtherRequests(Long shiftId, Long currentRequestId) {
		for (ShiftRequest request : requestRepository.findAllByShift_IdAndStatusIn(
				shiftId, ACTIVE_REQUEST_STATUSES)) {
			if (!request.getId().equals(currentRequestId)) {
				request.cancel();
				applicationRepository.findAllByRequest_IdOrderByCreatedAtAsc(request.getId())
						.forEach(RequestApplication::markNotSelected);
			}
		}
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
			throw new InvalidRequestStateException("지원자가 현재 근무 가능한 직원이 아니에요.");
		}
	}

	private void requireStoreManagement(Long actorId, Store store) {
		if (store.getOwner().getId().equals(actorId)) {
			return;
		}
		boolean activeManager = storeMemberRepository.findByStore_IdAndUser_Id(store.getId(), actorId)
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
				.filter(member -> member.getRole() == MemberRole.MANAGER)
				.isPresent();
		if (!activeManager) {
			throw new StoreManagementForbiddenException();
		}
	}

	private String normalizeRejectComment(String comment) {
		if (!StringUtils.hasText(comment)) {
			throw new InvalidShiftRequestException("반려 사유를 입력해 주세요.");
		}
		String normalized = comment.trim();
		if (normalized.length() > 200) {
			throw new InvalidShiftRequestException("반려 사유는 200자 이내로 입력해 주세요.");
		}
		return normalized;
	}

	private ShiftRequest findRequest(Long requestId) {
		return requestRepository.findDetailedById(requestId).orElseThrow(ShiftRequestNotFoundException::new);
	}

	private RequestApplication findApplication(Long applicationId) {
		return applicationRepository.findDetailedById(applicationId)
				.orElseThrow(RequestApplicationNotFoundException::new);
	}

	public enum ApprovalDecision {
		APPROVE,
		REJECT
	}

	public record PendingApproval(ShiftRequest request, RequestApplication selectedApplication) {
	}
}
