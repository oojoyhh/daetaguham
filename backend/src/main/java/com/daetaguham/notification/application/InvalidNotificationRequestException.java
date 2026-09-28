package com.daetaguham.notification.application;

public class InvalidNotificationRequestException extends RuntimeException {
	public InvalidNotificationRequestException(String message) {
		super(message);
	}
}
