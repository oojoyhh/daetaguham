package com.daetaguham.request.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.daetaguham.request.application.RequestResolutionService.PendingApproval;
import com.daetaguham.request.domain.RequestType;
import com.daetaguham.shift.domain.Shift;

public record ApprovalItemResponse(
		RequestDetailResponse request,
		ApplicationResponse selectedApplication,
		LocalDateTime agreedAt,
		List<ApprovalChangeResponse> changes,
		List<ApprovalCheckResponse> checks
) {
	public static ApprovalItemResponse from(
			PendingApproval pending,
			RequestDetailResponse requestResponse,
			ApplicationResponse applicationResponse
	) {
		Shift original = pending.request().getShift();
		var applicant = pending.selectedApplication().getApplicant();
		List<ApprovalChangeResponse> changes = new ArrayList<>();
		changes.add(new ApprovalChangeResponse(
				original.getStartAt().toLocalDate(),
				ApprovalShiftResponse.from(original, original.getWorker()),
				ApprovalShiftResponse.from(original, applicant)
		));
		if (pending.request().getType() == RequestType.EXCHANGE) {
			Shift offered = pending.selectedApplication().getSelectedOfferShift();
			changes.add(new ApprovalChangeResponse(
					offered.getStartAt().toLocalDate(),
					ApprovalShiftResponse.from(offered, offered.getWorker()),
					ApprovalShiftResponse.from(offered, pending.request().getRequester())
			));
		}
		return new ApprovalItemResponse(
				requestResponse,
				applicationResponse,
				pending.selectedApplication().getRespondedAt(),
				changes,
				List.of(
						new ApprovalCheckResponse("OK", "현재 근무 담당자와 지원자 자격을 확인했어요."),
						new ApprovalCheckResponse("OK", "승인 시 근무 겹침을 다시 검사해요.")
				)
		);
	}
}
