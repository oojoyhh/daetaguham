package com.daetaguham.store.application;

public class OwnerPermissionRequiredException extends RuntimeException {

	public OwnerPermissionRequiredException() {
		super("해당 매장의 사장만 직원 역할을 변경할 수 있어요.");
	}
}
