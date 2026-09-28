package com.daetaguham.request.application;

public class RequestApplicationNotFoundException extends RuntimeException {

	public RequestApplicationNotFoundException() {
		super("지원 또는 제안을 찾을 수 없어요.");
	}
}
