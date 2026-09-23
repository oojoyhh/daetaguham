package com.daetaguham.shift.api;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.daetaguham.shift.application.ShiftService.TemplateCommand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShiftTemplateWriteRequest(
		@NotBlank(message = "시간대 이름을 입력해 주세요.")
		@Size(max = 10, message = "시간대 이름은 10자 이하로 입력해 주세요.")
		@Pattern(regexp = "^[^|]+$", message = "시간대 이름에 | 문자를 사용할 수 없어요.")
		String name,

		@NotNull(message = "시작 시간을 입력해 주세요.")
		@JsonFormat(pattern = "HH:mm")
		LocalTime startTime,

		@NotNull(message = "종료 시간을 입력해 주세요.")
		@JsonFormat(pattern = "HH:mm")
		LocalTime endTime,

		@Min(value = 1, message = "필요 인원은 1명 이상이어야 해요.")
		@Max(value = 9, message = "필요 인원은 9명 이하여야 해요.")
		Integer requiredCount
) {
	public TemplateCommand toCommand() {
		return new TemplateCommand(name, startTime, endTime, requiredCount == null ? 1 : requiredCount);
	}
}
