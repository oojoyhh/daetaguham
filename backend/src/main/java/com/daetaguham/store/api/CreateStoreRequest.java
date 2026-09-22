package com.daetaguham.store.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
		@NotBlank(message = "매장 이름을 입력해 주세요.")
		@Size(max = 50, message = "매장 이름은 50자 이하로 입력해 주세요.")
		String name,

		@NotBlank(message = "업종을 입력해 주세요.")
		@Size(max = 30, message = "업종은 30자 이하로 입력해 주세요.")
		String category,

		@Size(max = 200, message = "주소는 200자 이하로 입력해 주세요.")
		String address
) {
}
