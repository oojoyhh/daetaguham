package com.daetaguham.request.application;

public class ShiftRequestNotFoundException extends RuntimeException {

	public ShiftRequestNotFoundException() {
		super("요청을 찾을 수 없어요.");
	}
}
