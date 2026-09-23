package com.daetaguham.request.application;

public class InvalidRequestStateException extends RuntimeException {

	public InvalidRequestStateException(String message) {
		super(message);
	}
}
