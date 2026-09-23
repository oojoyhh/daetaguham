package com.daetaguham.request.api;

import java.time.LocalDate;
import java.util.List;

import com.daetaguham.request.domain.RequestMode;
import com.daetaguham.request.domain.RequestScope;
import com.daetaguham.request.domain.RequestType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestCreateRequest(
		@NotNull(message = "요청 유형을 선택해 주세요.") RequestType type,
		@NotNull(message = "근무를 선택해 주세요.") Long shiftId,
		RequestMode mode,
		RequestScope scope,
		Long targetUserId,
		List<Long> targetShiftIds,
		List<LocalDate> availableDates,
		@Size(max = 100, message = "요청 사유는 100자 이내로 입력해 주세요.") String reason
) {
}
