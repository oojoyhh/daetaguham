package com.daetaguham.shift.api;

import java.time.LocalDateTime;

import com.daetaguham.shift.application.ShiftService.ManagedShift;
import com.daetaguham.shift.domain.Shift;

public record ShiftResponse(
		Long id,
		Long storeId,
		Long workerId,
		String workerName,
		LocalDateTime startAt,
		LocalDateTime endAt,
		String position,
		boolean isHelper,
		Object openRequest
) {
	public static ShiftResponse from(ManagedShift managedShift) {
		Shift shift = managedShift.shift();
		return new ShiftResponse(
				shift.getId(),
				shift.getStore().getId(),
				shift.getWorker() == null ? null : shift.getWorker().getId(),
				shift.getWorker() == null ? null : shift.getWorker().getName(),
				shift.getStartAt(),
				shift.getEndAt(),
				shift.getPosition(),
				managedShift.helper(),
				null
		);
	}
}
