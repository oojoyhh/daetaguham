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
		return new StoreResponse(
				store.getId(),
				store.getOwner().getId(),
				store.getOwner().getName(),
				store.getName(),
				store.getCategory(),
				store.getAddress(),
				store.getInviteCode(),
				store.isApprovalRequired(),
				store.getCreatedAt()
		);
	}
}
