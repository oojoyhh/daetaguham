package com.daetaguham.store.api;

import java.util.List;

import com.daetaguham.shift.api.ShiftResponse;
import com.daetaguham.store.application.StoreOverviewService.StoreOverview;

public record StoreOverviewResponse(
		int todayWorkerCount,
		int pendingApprovalCount,
		int pendingMemberCount,
		int emptyShiftCount,
		int openShiftCount,
		List<ShiftResponse> todayShifts,
		List<ShiftResponse> emptyShifts
) {
	public static StoreOverviewResponse from(StoreOverview overview) {
		return new StoreOverviewResponse(
				overview.todayWorkerCount(),
				overview.pendingApprovalCount(),
				overview.pendingMemberCount(),
				overview.emptyShiftCount(),
				overview.openShiftCount(),
				overview.todayShifts().stream().map(ShiftResponse::from).toList(),
				overview.emptyShifts().stream().map(ShiftResponse::from).toList()
		);
	}
}
