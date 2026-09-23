package com.daetaguham.shift.api;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.daetaguham.shift.domain.ShiftTemplate;

public record ShiftTemplateResponse(
		Long id,
		String name,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime,
		int requiredCount
) {
	public static ShiftTemplateResponse from(ShiftTemplate template) {
		return new ShiftTemplateResponse(
				template.getId(),
				template.getName(),
				template.getStartTime(),
				template.getEndTime(),
				template.getRequiredCount()
		);
	}
}
