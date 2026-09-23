package com.daetaguham.shift.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ShiftBulkRequest(
		@NotNull(message = "저장 시작일을 입력해 주세요.")
		LocalDate from,

		@NotNull(message = "저장 종료일을 입력해 주세요.")
		LocalDate to,

		@NotNull(message = "근무 목록을 보내 주세요.")
		List<@Valid ShiftBulkItem> shifts
) {
}
