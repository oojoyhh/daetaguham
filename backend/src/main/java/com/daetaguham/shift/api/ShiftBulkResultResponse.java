package com.daetaguham.shift.api;

import java.util.List;

import com.daetaguham.shift.application.ShiftService.BulkSaveResult;

public record ShiftBulkResultResponse(
		int created,
		int deleted,
		List<Long> emptyShiftIds
) {
	public static ShiftBulkResultResponse from(BulkSaveResult result) {
		return new ShiftBulkResultResponse(result.created(), result.deleted(), result.emptyShiftIds());
	}
}
