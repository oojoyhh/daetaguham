package com.daetaguham.shift.application;

public class ShiftReferencedByRequestException extends RuntimeException {

	public ShiftReferencedByRequestException() {
		super("요청 기록이 연결된 근무는 수정하거나 삭제할 수 없어요.");
	}
}
