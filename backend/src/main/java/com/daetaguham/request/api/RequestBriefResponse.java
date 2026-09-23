package com.daetaguham.request.api;

import com.daetaguham.request.domain.ShiftRequest;

public record RequestBriefResponse(
		Long id,
		String type,
		String mode,
		String scope,
		String status
) {
	public static RequestBriefResponse from(ShiftRequest request) {
		return new RequestBriefResponse(
				request.getId(),
				request.getType().name(),
				request.getMode().name(),
				request.getScope().name(),
				request.getStatus().name()
		);
	}
}
