package com.daetaguham.store.application;

public class StoreNotFoundException extends RuntimeException {

	public StoreNotFoundException() {
		super("해당 초대코드의 매장을 찾을 수 없어요.");
	}
}
