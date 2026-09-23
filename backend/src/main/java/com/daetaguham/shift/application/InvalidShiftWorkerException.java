package com.daetaguham.shift.application;

public class InvalidShiftWorkerException extends RuntimeException {

	public InvalidShiftWorkerException() {
		super("근무자는 사장이 아닌 승인된 직원이어야 해요.");
	}
}
