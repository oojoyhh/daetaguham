package com.daetaguham.shift.application;

public class ShiftNotFoundException extends RuntimeException {

	public ShiftNotFoundException() {
		super("근무를 찾을 수 없어요.");
	}
}
