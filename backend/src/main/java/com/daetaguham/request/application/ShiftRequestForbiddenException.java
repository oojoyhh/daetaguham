package com.daetaguham.request.application;

public class ShiftRequestForbiddenException extends RuntimeException {

	public ShiftRequestForbiddenException() {
		super("이 요청을 확인하거나 변경할 권한이 없어요.");
	}
}
