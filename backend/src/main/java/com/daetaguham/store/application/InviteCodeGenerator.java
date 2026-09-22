package com.daetaguham.store.application;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

	private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int CODE_LENGTH = 6;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		char[] code = new char[CODE_LENGTH];
		for (int index = 0; index < CODE_LENGTH; index++) {
			code[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
		}
		return new String(code);
	}
}
