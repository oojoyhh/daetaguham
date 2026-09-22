package com.daetaguham.user.api;

import java.util.List;

import com.daetaguham.user.application.UserAccountService.LoginResult;

public record LoginResponse(
		String token,
		UserResponse user,
		List<MyStoreResponse> stores
) {
	public static LoginResponse from(LoginResult result) {
		return new LoginResponse(
				result.token(),
				UserResponse.from(result.account().user()),
				result.account().stores().stream().map(MyStoreResponse::from).toList()
		);
	}
}
