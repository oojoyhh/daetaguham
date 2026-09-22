package com.daetaguham.user.application;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("휴대폰 번호 또는 비밀번호를 확인해 주세요.");
	}
}
