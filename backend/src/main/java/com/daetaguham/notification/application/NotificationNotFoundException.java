package com.daetaguham.notification.application;

public class NotificationNotFoundException extends RuntimeException {
	public NotificationNotFoundException() {
		super("알림을 찾을 수 없어요.");
	}
}
