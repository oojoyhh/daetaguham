package com.daetaguham.shift.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ShiftBulkConflictException extends RuntimeException {

	private final List<Conflict> conflicts;

	public ShiftBulkConflictException(List<Conflict> conflicts) {
		super("겹치는 근무가 있어 저장하지 않았어요.");
		this.conflicts = List.copyOf(conflicts);
	}

	public List<Conflict> getConflicts() {
		return conflicts;
	}

	public record Conflict(
			Long workerId,
			String workerName,
			LocalDate date,
			String storeName,
			LocalDateTime startAt,
			LocalDateTime endAt
	) {
	}
}
