package com.daetaguham.notification.api;

import java.util.List;

import com.daetaguham.notification.application.NotificationService.NotificationPage;

public record NotificationPageResponse(
		long totalElements,
		int totalPages,
		int page,
		int size,
		long unreadCount,
		List<NotificationResponse> content
) {
	public static NotificationPageResponse from(NotificationPage result) {
		return new NotificationPageResponse(
				result.totalElements(),
				result.totalPages(),
				result.page(),
				result.size(),
				result.unreadCount(),
				result.content().stream().map(NotificationResponse::from).toList()
		);
	}
}
