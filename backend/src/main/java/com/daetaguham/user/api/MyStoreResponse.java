package com.daetaguham.user.api;

import java.time.LocalDateTime;

import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.user.application.UserAccountService.UserStoreView;

public record MyStoreResponse(
		Long storeId,
		String storeName,
		String myRole,
		MemberStatus memberStatus,
		LocalDateTime joinedAt
) {
	public static MyStoreResponse from(UserStoreView store) {
		return new MyStoreResponse(
				store.storeId(),
				store.storeName(),
				store.myRole(),
				store.memberStatus(),
				store.joinedAt()
		);
	}
}
