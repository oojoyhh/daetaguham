package com.daetaguham.store.application;

public class StoreManagementForbiddenException extends RuntimeException {

	public StoreManagementForbiddenException() {
		super("해당 매장의 사장 또는 점장만 이 작업을 할 수 있어요.");
	}
}
