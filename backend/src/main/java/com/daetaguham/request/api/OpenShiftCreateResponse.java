package com.daetaguham.request.api;

import com.daetaguham.request.application.RequestService.OpenShiftResult;

public record OpenShiftCreateResponse(
		RequestDetailResponse request,
		int notifiedCount
) {
	public static OpenShiftCreateResponse from(OpenShiftResult result) {
		return new OpenShiftCreateResponse(
				RequestDetailResponse.from(result.request()),
				result.notifiedCount()
		);
	}
}
