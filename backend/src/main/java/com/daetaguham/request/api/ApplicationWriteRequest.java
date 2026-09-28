package com.daetaguham.request.api;

import java.util.List;

import jakarta.validation.constraints.Size;

public record ApplicationWriteRequest(
		List<Long> offeredShiftIds,
		@Size(max = 60, message = "지원 메시지는 60자 이내로 입력해 주세요.") String message
) {
}
