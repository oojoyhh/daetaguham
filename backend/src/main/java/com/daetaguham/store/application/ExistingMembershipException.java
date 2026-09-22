package com.daetaguham.store.application;

public class ExistingMembershipException extends RuntimeException {

	public ExistingMembershipException() {
		super("이미 참여 중이거나 승인을 기다리고 있는 매장이에요.");
	}
}
