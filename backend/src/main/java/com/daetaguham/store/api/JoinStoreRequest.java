package com.daetaguham.store.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinStoreRequest(
		@NotBlank(message = "초대코드를 입력해 주세요.")
		@Pattern(regexp = "^[A-Z0-9]{6}$", message = "초대코드는 영문 대문자와 숫자 6자리예요.")
		String inviteCode
) {
}
