package com.daetaguham.shift.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.daetaguham.shift.application.ShiftBulkConflictException;

public record ShiftBulkConflictResponse(
		String code,
		String message,
		List<ConflictResponse> conflicts,
		List<Long> referencedShiftIds
) {
	public static ShiftBulkConflictResponse from(ShiftBulkConflictException exception) {
		return new ShiftBulkConflictResponse(
				"TIME_CONFLICT",
				exception.getMessage(),
				exception.getConflicts().stream().map(ConflictResponse::from).toList(),
				List.of()
		);
	}

	public record ConflictResponse(
			Long workerId,
			String workerName,
			LocalDate date,
			String storeName,
			LocalDateTime startAt,
			LocalDateTime endAt
	) {
		private static ConflictResponse from(ShiftBulkConflictException.Conflict conflict) {
			return new ConflictResponse(
					conflict.workerId(),
					conflict.workerName(),
					conflict.date(),
					conflict.storeName(),
					conflict.startAt(),
					conflict.endAt()
			);
		}
	}
}
