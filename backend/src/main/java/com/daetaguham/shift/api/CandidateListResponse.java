package com.daetaguham.shift.api;

import java.util.List;

import com.daetaguham.shift.application.CandidateRecommendationService.CandidateResult;

public record CandidateListResponse(
		List<CandidateResponse> candidates,
		int excludedCount
) {
	public static CandidateListResponse from(CandidateResult result) {
		return new CandidateListResponse(
				result.candidates().stream().map(CandidateResponse::from).toList(),
				result.excludedCount()
		);
	}
}
