package com.daetaguham.user.api;

import java.util.List;

import com.daetaguham.user.application.UserAccountService.AccountResult;

public record MeResponse(
		UserResponse user,
		List<MyStoreResponse> stores
) {
	public static MeResponse from(AccountResult account) {
		return new MeResponse(
				UserResponse.from(account.user()),
				account.stores().stream().map(MyStoreResponse::from).toList()
		);
	}
}
