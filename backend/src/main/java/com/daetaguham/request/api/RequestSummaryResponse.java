package com.daetaguham.request.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.daetaguham.request.application.RequestQueryService.RequestSummaryView;
import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.shift.api.ShiftResponse;

public record RequestSummaryResponse(
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
		String myApplicationStatus,
		LocalDateTime createdAt
) {
	public static RequestSummaryResponse from(RequestSummaryView view) {
		ShiftRequest request = view.request();
		return new RequestSummaryResponse(
				request.getId(),
				request.getType().name(),
				request.getMode().name(),
				request.getScope().name(),
				request.getStatus().name(),
				request.getShift().getStore().getName(),
				request.getRequester().getName(),
				ShiftResponse.from(
						request.getShift(), view.helper(), RequestBriefResponse.from(request)),
				view.applicantCount(),
				view.availableDates(),
				view.myApplicationStatus() == null ? null : view.myApplicationStatus().name(),
				request.getCreatedAt()
		);
	}
}
