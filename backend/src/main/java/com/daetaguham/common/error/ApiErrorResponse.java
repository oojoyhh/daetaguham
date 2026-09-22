package com.daetaguham.common.error;

public record ApiErrorResponse(
		int code,
		String errorCode,
		String message
) {
}
