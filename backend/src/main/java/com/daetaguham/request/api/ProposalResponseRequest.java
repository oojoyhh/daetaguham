package com.daetaguham.request.api;

import jakarta.validation.constraints.NotNull;

public record ProposalResponseRequest(
		@NotNull(message = "수락 또는 거절을 선택해 주세요.") Boolean accept,
		Long offerShiftId
) {
}
