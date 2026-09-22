package com.daetaguham.store.application;

public class OwnerCannotJoinException extends RuntimeException {

	public OwnerCannotJoinException() {
		super("사장은 자신의 매장에 직원으로 참여할 수 없어요.");
	}
}
