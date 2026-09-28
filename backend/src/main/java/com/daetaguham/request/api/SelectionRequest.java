package com.daetaguham.request.api;

import jakarta.validation.constraints.NotNull;

public record SelectionRequest(
		@NotNull(message = "지원자를 선택해 주세요.") Long applicationId,
		Long offerShiftId
) {
}
