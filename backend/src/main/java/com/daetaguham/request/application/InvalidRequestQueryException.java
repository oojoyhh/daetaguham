package com.daetaguham.request.application;

public class InvalidRequestQueryException extends RuntimeException {
	public InvalidRequestQueryException(String message) {
		super(message);
	}
}
