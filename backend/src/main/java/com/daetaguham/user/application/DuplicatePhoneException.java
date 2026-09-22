package com.daetaguham.user.application;

public class DuplicatePhoneException extends RuntimeException {

	public DuplicatePhoneException() {
		super("이미 가입된 휴대폰 번호예요.");
	}
}
