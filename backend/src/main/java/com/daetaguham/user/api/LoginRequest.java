package com.daetaguham.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
		@NotBlank(message = "휴대폰 번호를 입력해 주세요.")
		@Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식으로 입력해 주세요.")
		String phone,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		String password
) {
}
