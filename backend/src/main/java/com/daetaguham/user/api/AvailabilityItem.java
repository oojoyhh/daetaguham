package com.daetaguham.user.api;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.daetaguham.user.application.AvailabilityService.AvailabilityCommand;
import com.daetaguham.user.domain.Availability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AvailabilityItem(
		@NotNull(message = "요일을 선택해 주세요.")
		@Min(value = 0, message = "요일은 0(일)부터 6(토)까지로 입력해 주세요.")
		@Max(value = 6, message = "요일은 0(일)부터 6(토)까지로 입력해 주세요.")
		Integer dayOfWeek,

		@NotNull(message = "시작 시간을 입력해 주세요.")
		@JsonFormat(pattern = "HH:mm")
		LocalTime startTime,

		@NotNull(message = "종료 시간을 입력해 주세요.")
		@JsonFormat(pattern = "HH:mm")
		LocalTime endTime
) {
	public AvailabilityCommand toCommand() {
		return new AvailabilityCommand(dayOfWeek, startTime, endTime);
	}

	public static AvailabilityItem from(Availability availability) {
		return new AvailabilityItem(
				availability.getDayOfWeek(),
				availability.getStartTime(),
				availability.getEndTime()
		);
	}
}
