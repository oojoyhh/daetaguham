package com.daetaguham.shift.application;

public class ShiftTimeConflictException extends RuntimeException {

	public ShiftTimeConflictException() {
		super("같은 시간에 이미 배정된 근무가 있어요.");
	}
}
