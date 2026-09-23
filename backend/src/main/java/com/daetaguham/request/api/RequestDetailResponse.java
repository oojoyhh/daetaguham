package com.daetaguham.request.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.daetaguham.request.application.RequestService.RequestView;
import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.shift.api.ShiftResponse;

public record RequestDetailResponse(
		Long id,
		String type,
		String mode,
		String scope,
		String status,
		String storeName,
		String requesterName,
		ShiftResponse shift,
		int applicantCount,
		List<LocalDate> availableDates,
		Object myApplicationStatus,
		LocalDateTime createdAt,
		String reason,
		boolean approvalRequired,
		LocalDateTime confirmedAt,
		List<Object> applications,
		Object myApplication
) {
	public static RequestDetailResponse from(RequestView view) {
		ShiftRequest request = view.request();
		return new RequestDetailResponse(
				request.getId(),
				request.getType().name(),
				request.getMode().name(),
				request.getScope().name(),
				request.getStatus().name(),
				request.getShift().getStore().getName(),
				request.getRequester().getName(),
				ShiftResponse.from(request.getShift(), view.helper(), RequestBriefResponse.from(request)),
				0,
				view.availableDates(),
				null,
				request.getCreatedAt(),
				request.getReason(),
				view.approvalRequired(),
				request.getConfirmedAt(),
				List.of(),
				null
		);
	}
}
