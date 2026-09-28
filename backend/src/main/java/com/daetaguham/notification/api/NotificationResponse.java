package com.daetaguham.notification.api;

import java.time.LocalDateTime;

import com.daetaguham.notification.application.NotificationService.NotificationView;
import com.daetaguham.notification.domain.NotificationType;

public record NotificationResponse(
		Long id,
		NotificationType type,
		String message,
		Long requestId,
		Long storeId,
		boolean isRead,
		LocalDateTime createdAt
) {
	public static NotificationResponse from(NotificationView view) {
		return new NotificationResponse(
				view.id(),
				view.type(),
				view.message(),
				view.requestId(),
				view.storeId(),
				view.isRead(),
				view.createdAt()
		);
	}
}
