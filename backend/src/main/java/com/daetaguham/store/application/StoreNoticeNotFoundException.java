package com.daetaguham.store.application;

public class StoreNoticeNotFoundException extends RuntimeException {

	public StoreNoticeNotFoundException() {
		super("전달사항을 찾을 수 없어요.");
	}
}
