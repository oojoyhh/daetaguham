package com.daetaguham.user.api;

import java.time.LocalDateTime;

import com.daetaguham.user.domain.User;

public record UserResponse(
		Long id,
		String name,
		String phone,
		LocalDateTime createdAt
) {
	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getPhone(),
				user.getCreatedAt()
		);
	}
}
