package com.daetaguham.store.application;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreNotice;
import com.daetaguham.store.domain.StoreNoticeRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.application.InvalidCredentialsException;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreNoticeService {

	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final StoreNoticeRepository storeNoticeRepository;
	private final UserRepository userRepository;

	public StoreNoticeService(
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			StoreNoticeRepository storeNoticeRepository,
			UserRepository userRepository
	) {
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.storeNoticeRepository = storeNoticeRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<StoreNotice> findByStore(Long actorId, Long storeId) {
		Store store = findStore(storeId);
		requireStoreAccess(actorId, store);
		return storeNoticeRepository
				.findAllByStore_IdAndNoticeDateGreaterThanEqualOrderByNoticeDateAscCreatedAtAsc(
						storeId, LocalDate.now());
	}

	@Transactional(readOnly = true)
	public List<StoreNotice> findMine(Long userId) {
		Set<Long> storeIds = new LinkedHashSet<>();
		storeRepository.findAllByOwner_Id(userId).forEach(store -> storeIds.add(store.getId()));
		storeMemberRepository.findAllByUser_IdAndStatus(userId, MemberStatus.ACTIVE)
				.forEach(membership -> storeIds.add(membership.getStore().getId()));
		if (storeIds.isEmpty()) {
			return List.of();
		}
		return storeNoticeRepository
				.findAllByStore_IdInAndNoticeDateGreaterThanEqualOrderByNoticeDateAscCreatedAtAsc(
						storeIds, LocalDate.now());
	}

	@Transactional
	public StoreNotice create(Long actorId, Long storeId, LocalDate noticeDate, String content) {
		Store store = findStore(storeId);
		requireStoreManagement(actorId, store);
		validate(noticeDate, content);
		User actor = userRepository.findById(actorId).orElseThrow(InvalidCredentialsException::new);
		return storeNoticeRepository.save(StoreNotice.create(
				store, noticeDate, content.trim(), actor));
	}

	@Transactional
	public void delete(Long actorId, Long noticeId) {
		StoreNotice notice = storeNoticeRepository.findDetailedById(noticeId)
				.orElseThrow(StoreNoticeNotFoundException::new);
		requireStoreManagement(actorId, notice.getStore());
		storeNoticeRepository.delete(notice);
	}

	private Store findStore(Long storeId) {
		return storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
	}

	private void requireStoreAccess(Long actorId, Store store) {
		if (store.getOwner().getId().equals(actorId)) {
			return;
		}
		if (!storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
				store.getId(), actorId, MemberStatus.ACTIVE)) {
			throw new StoreManagementForbiddenException();
		}
	}

	private void requireStoreManagement(Long actorId, Store store) {
		if (store.getOwner().getId().equals(actorId)) {
			return;
		}
		boolean activeManager = storeMemberRepository.findByStore_IdAndUser_Id(store.getId(), actorId)
				.filter(membership -> membership.getStatus() == MemberStatus.ACTIVE)
				.filter(membership -> membership.getRole() == MemberRole.MANAGER)
				.isPresent();
		if (!activeManager) {
			throw new StoreManagementForbiddenException();
		}
	}

	private void validate(LocalDate noticeDate, String content) {
		if (noticeDate.isBefore(LocalDate.now())) {
			throw new InvalidStoreNoticeException("전달사항 날짜는 오늘 이후로 선택해 주세요.");
		}
		String normalized = content == null ? "" : content.trim();
		if (normalized.isEmpty() || normalized.length() > 160) {
			throw new InvalidStoreNoticeException("전달사항은 1~160자로 입력해 주세요.");
		}
	}
}
