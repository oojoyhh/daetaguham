package com.daetaguham.store.application;

public class StoreNotFoundException extends RuntimeException {

	public StoreNotFoundException() {
		this("매장을 찾을 수 없어요.");
	}

	public StoreNotFoundException(String message) {
		super(message);
	}
}
