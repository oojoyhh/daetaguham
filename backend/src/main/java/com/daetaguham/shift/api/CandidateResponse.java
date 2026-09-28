package com.daetaguham.shift.api;

import java.util.List;

import com.daetaguham.shift.application.CandidateRecommendationService.CandidateView;

public record CandidateResponse(
		Long userId,
		String name,
		String storeName,
		int score,
		double weekHours,
		int gaveToMe,
		String aiReason,
		List<ShiftResponse> shifts
) {
	public static CandidateResponse from(CandidateView candidate) {
		return new CandidateResponse(
				candidate.user().getId(),
				candidate.user().getName(),
				candidate.storeName(),
				candidate.score(),
				candidate.weekHours(),
				candidate.gaveToMe(),
				candidate.aiReason(),
				candidate.shifts().stream()
						.map(shift -> ShiftResponse.from(shift, false, null))
						.toList()
		);
	}
}
