package com.daetaguham.store.application;

import java.util.List;

import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreMemberManagementService {

	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;

	public StoreMemberManagementService(
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository
	) {
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
	}

	@Transactional(readOnly = true)
	public List<StoreMember> findMembers(Long actorId, Long storeId, MemberStatus status) {
		Store store = findStore(storeId);
		requireOwnerOrManager(actorId, store);
		if (status == null) {
			return storeMemberRepository.findAllByStore_IdOrderByRequestedAtAsc(storeId);
		}
		return storeMemberRepository.findAllByStore_IdAndStatusOrderByRequestedAtAsc(storeId, status);
	}

	@Transactional
	public StoreMember decideStatus(Long actorId, Long storeId, Long userId, MemberStatus decision) {
		Store store = findStore(storeId);
		requireOwnerOrManager(actorId, store);
		StoreMember membership = findMembership(storeId, userId);
		if (membership.getStatus() != MemberStatus.PENDING) {
			throw new InvalidMemberStateException("대기 중인 참여 신청만 승인하거나 거절할 수 있어요.");
		}
		membership.decide(decision);
		return membership;
	}

	@Transactional
	public StoreMember changeRole(Long actorId, Long storeId, Long userId, MemberRole role) {
		Store store = findStore(storeId);
		if (!store.getOwner().getId().equals(actorId)) {
			throw new OwnerPermissionRequiredException();
		}
		StoreMember membership = findMembership(storeId, userId);
		if (membership.getStatus() != MemberStatus.ACTIVE) {
			throw new InvalidMemberStateException("승인된 직원의 역할만 변경할 수 있어요.");
		}
		membership.changeRole(role);
		return membership;
	}

	private Store findStore(Long storeId) {
		return storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
	}

	private StoreMember findMembership(Long storeId, Long userId) {
		return storeMemberRepository.findByStore_IdAndUser_Id(storeId, userId)
				.orElseThrow(StoreMemberNotFoundException::new);
	}

	private void requireOwnerOrManager(Long actorId, Store store) {
		if (store.getOwner().getId().equals(actorId)) {
			return;
		}
		boolean isActiveManager = storeMemberRepository.findByStore_IdAndUser_Id(store.getId(), actorId)
				.filter(membership -> membership.getStatus() == MemberStatus.ACTIVE)
				.filter(membership -> membership.getRole() == MemberRole.MANAGER)
				.isPresent();
		if (!isActiveManager) {
			throw new StoreManagementForbiddenException();
		}
	}
}
