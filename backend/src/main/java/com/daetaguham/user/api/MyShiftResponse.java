package com.daetaguham.user.api;

import java.time.LocalDateTime;

import com.daetaguham.shift.application.ShiftProgress;
import com.daetaguham.shift.application.ShiftService.MyShift;
import com.daetaguham.shift.domain.Shift;

public record MyShiftResponse(
		Long id,
		Long storeId,
		Long workerId,
		String workerName,
		LocalDateTime startAt,
		LocalDateTime endAt,
		String position,
		boolean isHelper,
		Object openRequest,
		String storeName,
		ShiftProgress status
) {
	public static MyShiftResponse from(MyShift myShift) {
		Shift shift = myShift.shift();
		return new MyShiftResponse(
				shift.getId(),
				shift.getStore().getId(),
				shift.getWorker().getId(),
				shift.getWorker().getName(),
				shift.getStartAt(),
				shift.getEndAt(),
				shift.getPosition(),
				myShift.helper(),
				null,
				shift.getStore().getName(),
				myShift.status()
		);
	}
}
