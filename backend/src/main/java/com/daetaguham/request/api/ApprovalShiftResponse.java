package com.daetaguham.request.api;

import java.time.LocalDateTime;

import com.daetaguham.shift.domain.Shift;
import com.daetaguham.user.domain.User;

public record ApprovalShiftResponse(
		Long id,
		Long storeId,
		Long workerId,
		String workerName,
		LocalDateTime startAt,
		LocalDateTime endAt,
		String position
) {
	public static ApprovalShiftResponse from(Shift shift, User worker) {
		return new ApprovalShiftResponse(
				shift.getId(),
				shift.getStore().getId(),
				worker == null ? null : worker.getId(),
				worker == null ? null : worker.getName(),
				shift.getStartAt(),
				shift.getEndAt(),
				shift.getPosition()
		);
	}
}
