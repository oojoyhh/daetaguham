package com.daetaguham.request.application;

public class ActiveShiftRequestExistsException extends RuntimeException {

	public ActiveShiftRequestExistsException() {
		super("이 근무에는 이미 진행 중인 요청이 있어요.");
	}
}
