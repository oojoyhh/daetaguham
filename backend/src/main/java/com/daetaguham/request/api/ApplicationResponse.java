package com.daetaguham.request.api;

import java.time.LocalDateTime;
import java.util.List;

import com.daetaguham.request.application.ApplicationService.ApplicationView;
import com.daetaguham.request.domain.RequestApplication;
import com.daetaguham.shift.api.ShiftResponse;

public record ApplicationResponse(
		Long id,
		Long applicantId,
		String applicantName,
		String applicantStoreName,
		int helperCount,
		String aiReason,
		String status,
		List<ShiftResponse> offeredShifts,
		ShiftResponse selectedOfferShift,
		String message,
		double weekHours,
		GiveTakeResponse giveTake,
		String rejectComment,
		LocalDateTime createdAt
) {
	public static ApplicationResponse from(ApplicationView view) {
		RequestApplication application = view.application();
		return new ApplicationResponse(
				application.getId(),
				application.getApplicant().getId(),
				application.getApplicant().getName(),
				view.applicantStoreName(),
				view.helperCount(),
				null,
				application.getStatus().name(),
				view.offeredShifts().stream()
						.map(shift -> ShiftResponse.from(shift, false, null))
						.toList(),
				application.getSelectedOfferShift() == null
						? null
						: ShiftResponse.from(application.getSelectedOfferShift(), false, null),
				application.getMessage(),
				view.weekHours(),
				new GiveTakeResponse(view.gaveToMe(), view.gotFromMe()),
				application.getRejectComment(),
				application.getCreatedAt()
		);
	}
}
