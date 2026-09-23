package com.daetaguham.store.application;

public class StoreMemberNotFoundException extends RuntimeException {

	public StoreMemberNotFoundException() {
		super("해당 매장의 직원을 찾을 수 없어요.");
	}
}
