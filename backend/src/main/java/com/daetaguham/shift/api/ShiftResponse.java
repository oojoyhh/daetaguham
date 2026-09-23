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
		return from(managedShift.shift(), managedShift.helper(), null);
	}

	public static ShiftResponse from(Shift shift, boolean helper, Object openRequest) {
		return new ShiftResponse(
				shift.getId(),
				shift.getStore().getId(),
				shift.getWorker() == null ? null : shift.getWorker().getId(),
				shift.getWorker() == null ? null : shift.getWorker().getName(),
				shift.getStartAt(),
				shift.getEndAt(),
				shift.getPosition(),
				helper,
				openRequest
		);
	}
}
