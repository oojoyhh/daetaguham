package com.daetaguham.store.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StoreNoticeWriteRequest(
		@NotNull(message = "전달사항 날짜를 선택해 주세요.")
		LocalDate noticeDate,

		@NotBlank(message = "전달사항 내용을 입력해 주세요.")
		@Size(max = 160, message = "전달사항은 160자 이하로 입력해 주세요.")
		String content
) {
}
