package com.daetaguham.shift.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShiftWriteRequest(
		Long workerId,

		@NotNull(message = "근무 시작 시간을 입력해 주세요.")
		LocalDateTime startAt,

		@NotNull(message = "근무 종료 시간을 입력해 주세요.")
		LocalDateTime endAt,

		@Size(max = 10, message = "시간대 이름은 10자 이하로 입력해 주세요.")
		String position
) {
}
