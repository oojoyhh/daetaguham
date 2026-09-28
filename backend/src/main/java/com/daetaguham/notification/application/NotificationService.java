package com.daetaguham.notification.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.daetaguham.notification.domain.Notification;
import com.daetaguham.notification.domain.NotificationRepository;
import com.daetaguham.notification.domain.NotificationType;
import com.daetaguham.request.domain.RequestType;
import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final ShiftRepository shiftRepository;
	private final UserRepository userRepository;

	public NotificationService(
			NotificationRepository notificationRepository,
			StoreMemberRepository storeMemberRepository,
			ShiftRepository shiftRepository,
			UserRepository userRepository
	) {
		this.notificationRepository = notificationRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.shiftRepository = shiftRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public NotificationPage findMine(Long userId, boolean unreadOnly, int page, int size) {
		if (page < 1) {
			throw new InvalidNotificationRequestException("페이지는 1 이상이어야 해요.");
		}
		if (size < 1 || size > 100) {
			throw new InvalidNotificationRequestException("페이지 크기는 1~100 사이여야 해요.");
		}
		PageRequest pageable = PageRequest.of(page - 1, size);
		Page<Notification> result = unreadOnly
				? notificationRepository.findAllByUser_IdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
				: notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(userId, pageable);
		return new NotificationPage(
				result.getTotalElements(),
				result.getTotalPages(),
				page,
				size,
				notificationRepository.countByUser_IdAndIsReadFalse(userId),
				result.getContent().stream().map(this::toView).toList()
		);
	}

	@Transactional
	public void markRead(Long userId, Long notificationId) {
		Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
				.orElseThrow(NotificationNotFoundException::new);
		notification.markRead();
	}

	@Transactional
	public void markAllRead(Long userId) {
		notificationRepository.markAllReadByUserId(userId);
	}

	@Transactional
	public int notifyPublicRequest(ShiftRequest request, Collection<LocalDate> availableDates) {
		Shift shift = request.getShift();
		Store store = shift.getStore();
		List<StoreMember> memberships = request.getScope() == com.daetaguham.request.domain.RequestScope.STORE
				? storeMemberRepository.findAllByStore_IdAndStatusOrderByRequestedAtAsc(
						store.getId(), MemberStatus.ACTIVE)
				: storeMemberRepository.findAllByStatusAndStore_Owner_Id(
						MemberStatus.ACTIVE, store.getOwner().getId());

		Map<Long, User> candidates = new LinkedHashMap<>();
		for (StoreMember membership : memberships) {
			User candidate = membership.getUser();
			if (candidate.getId().equals(request.getRequester().getId())) {
				continue;
			}
			if (!shiftRepository.findOverlappingShifts(
					candidate.getId(), shift.getStartAt(), shift.getEndAt()).isEmpty()) {
				continue;
			}
			if (request.getType() == RequestType.EXCHANGE
					&& !hasExchangeShift(candidate.getId(), store.getId(), availableDates)) {
				continue;
			}
			candidates.putIfAbsent(candidate.getId(), candidate);
		}

		String requestName = request.getType() == RequestType.EXCHANGE ? "교대" : "대타";
		String message = request.getRequester().getName() + "님이 " + store.getName()
				+ " " + requestName + " 요청을 올렸어요.";
		return saveAll(candidates.values(), NotificationType.REQUEST_OPENED, request, store, message);
	}

	@Transactional
	public int notifyOpenShift(ShiftRequest request, Collection<Long> targetUserIds) {
		Set<Long> uniqueIds = new LinkedHashSet<>(targetUserIds);
		List<User> targets = userRepository.findAllById(uniqueIds);
		String message = request.getShift().getStore().getName() + "에 "
				+ request.getShift().getPosition() + " 급구가 올라왔어요.";
		return saveAll(
				targets,
				NotificationType.OPEN_SHIFT_POSTED,
				request,
				request.getShift().getStore(),
				message
		);
	}

	@Transactional
	public void notifyProposalReceived(ShiftRequest request, User target) {
		String requestName = request.getType() == RequestType.EXCHANGE ? "교대" : "대타";
		save(target, NotificationType.PROPOSAL_RECEIVED, request,
				request.getShift().getStore(),
				request.getRequester().getName() + "님이 " + requestName + "를 제안했어요.");
	}

	@Transactional
	public void notifyApplicationNew(ShiftRequest request, User applicant) {
		save(request.getRequester(), NotificationType.APPLICATION_NEW, request,
				request.getShift().getStore(),
				applicant.getName() + "님이 요청에 지원했어요.");
	}

	@Transactional
	public void notifyProposalDeclined(ShiftRequest request, User target) {
		save(request.getRequester(), NotificationType.REJECTED, request,
				request.getShift().getStore(),
				target.getName() + "님이 지정 제안을 거절했어요.");
	}

	@Transactional
	public int notifyApprovalNeeded(ShiftRequest request) {
		Store store = request.getShift().getStore();
		List<User> managers = managementUsers(store);
		return saveAll(managers, NotificationType.APPROVAL_NEEDED, request, store,
				request.getRequester().getName() + "님의 근무 변경 승인이 필요해요.");
	}

	@Transactional
	public void notifyConfirmed(ShiftRequest request, User applicant) {
		Set<User> recipients = new LinkedHashSet<>();
		recipients.add(request.getRequester());
		recipients.add(applicant);
		String message = request.getShift().getStartAt().toLocalDate() + " "
				+ request.getShift().getPosition() + " 근무 변경이 확정됐어요.";
		saveAll(recipients, NotificationType.CONFIRMED, request,
				request.getShift().getStore(), message);
	}

	@Transactional
	public void notifyApprovalRejected(ShiftRequest request, User applicant, String comment) {
		Set<User> recipients = new LinkedHashSet<>();
		recipients.add(request.getRequester());
		recipients.add(applicant);
		String message = "근무 변경이 반려됐어요: " + comment;
		saveAll(recipients, NotificationType.REJECTED, request,
				request.getShift().getStore(), truncate(message));
	}

	@Transactional
	public int notifyMemberRequest(Store store, User applicant) {
		return saveAll(managementUsers(store), NotificationType.MEMBER_REQUEST, null, store,
				applicant.getName() + "님이 " + store.getName() + " 참여를 신청했어요.");
	}

	@Transactional
	public int notifyExpired(ShiftRequest request, Collection<User> participants) {
		Map<Long, User> recipients = new LinkedHashMap<>();
		recipients.put(request.getRequester().getId(), request.getRequester());
		participants.forEach(user -> recipients.putIfAbsent(user.getId(), user));
		String position = request.getShift().getPosition();
		String shiftLabel = position == null || position.isBlank() ? "근무" : position + " 근무";
		String message = request.getShift().getStartAt().toLocalDate() + " "
				+ shiftLabel + " 요청이 마감됐어요.";
		return saveAll(
				recipients.values(),
				NotificationType.EXPIRED,
				request,
				request.getShift().getStore(),
				message
		);
	}

	private List<User> managementUsers(Store store) {
		Map<Long, User> users = new LinkedHashMap<>();
		users.put(store.getOwner().getId(), store.getOwner());
		storeMemberRepository.findAllByStore_IdAndStatusOrderByRequestedAtAsc(
				store.getId(), MemberStatus.ACTIVE).stream()
				.filter(member -> member.getRole() == MemberRole.MANAGER)
				.map(StoreMember::getUser)
				.forEach(user -> users.putIfAbsent(user.getId(), user));
		return List.copyOf(users.values());
	}

	private boolean hasExchangeShift(Long userId, Long storeId, Collection<LocalDate> dates) {
		for (LocalDate date : dates) {
			List<Shift> shifts = shiftRepository
					.findAllByWorker_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
							userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
			if (shifts.stream().anyMatch(shift -> shift.getStore().getId().equals(storeId))) {
				return true;
			}
		}
		return false;
	}

	private int saveAll(
			Collection<User> users,
			NotificationType type,
			ShiftRequest request,
			Store store,
			String message
	) {
		List<Notification> notifications = users.stream()
				.collect(java.util.stream.Collectors.toMap(
						User::getId,
						user -> Notification.create(user, type, request, store, truncate(message)),
						(first, ignored) -> first,
						LinkedHashMap::new
				))
				.values().stream().toList();
		notificationRepository.saveAll(notifications);
		return notifications.size();
	}

	private void save(
			User user,
			NotificationType type,
			ShiftRequest request,
			Store store,
			String message
	) {
		notificationRepository.save(Notification.create(user, type, request, store, truncate(message)));
	}

	private String truncate(String message) {
		return message.length() <= 200 ? message : message.substring(0, 200);
	}

	private NotificationView toView(Notification notification) {
		return new NotificationView(
				notification.getId(),
				notification.getType(),
				notification.getMessage(),
				notification.getRequest() == null ? null : notification.getRequest().getId(),
				notification.getStore() == null ? null : notification.getStore().getId(),
				notification.isRead(),
				notification.getCreatedAt()
		);
	}

	public record NotificationView(
			Long id,
			NotificationType type,
			String message,
			Long requestId,
			Long storeId,
			boolean isRead,
			LocalDateTime createdAt
	) {
	}

	public record NotificationPage(
			long totalElements,
			int totalPages,
			int page,
			int size,
			long unreadCount,
			List<NotificationView> content
	) {
	}
}
