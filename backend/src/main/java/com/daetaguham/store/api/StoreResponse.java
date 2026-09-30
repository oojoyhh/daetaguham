package com.daetaguham.store.api;

import java.time.LocalDateTime;

import com.daetaguham.store.domain.Store;

public record StoreResponse(
		Long id,
		Long ownerId,
		String ownerName,
		String name,
		String category,
		String address,
		String inviteCode,
		boolean approvalRequired,
		LocalDateTime createdAt
) {
	public static StoreResponse from(Store store) {
		return from(store, true);
	}

	public static StoreResponse from(Store store, boolean includeInviteCode) {
		return new StoreResponse(
				store.getId(),
				store.getOwner().getId(),
				store.getOwner().getName(),
				store.getName(),
				store.getCategory(),
				store.getAddress(),
				includeInviteCode ? store.getInviteCode() : null,
				store.isApprovalRequired(),
				store.getCreatedAt()
		);
	}
}
