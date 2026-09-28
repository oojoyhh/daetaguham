package com.daetaguham.request.api;

import java.util.List;

import com.daetaguham.request.application.RequestQueryService.RequestPage;

public record RequestPageResponse(
		long totalElements,
		int totalPages,
		int page,
		int size,
		List<RequestSummaryResponse> content
) {
	public static RequestPageResponse from(RequestPage result) {
		return new RequestPageResponse(
				result.totalElements(),
				result.totalPages(),
				result.page(),
				result.size(),
				result.content().stream().map(RequestSummaryResponse::from).toList()
		);
	}
}
