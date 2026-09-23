package com.daetaguham.shift.api;

import java.time.LocalDate;

import com.daetaguham.shift.application.ShiftService.BulkItemCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShiftBulkItem(
		Long workerId,

		@NotNull(message = "근무 날짜를 입력해 주세요.")
		LocalDate date,

		@NotBlank(message = "시간대를 입력해 주세요.")
		@Size(max = 10, message = "시간대 이름은 10자 이하로 입력해 주세요.")
		String position
) {
	public BulkItemCommand toCommand() {
		return new BulkItemCommand(workerId, date, position);
	}
}
