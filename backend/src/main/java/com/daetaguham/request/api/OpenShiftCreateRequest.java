package com.daetaguham.request.api;

import java.time.LocalDateTime;
import java.util.List;

import com.daetaguham.request.domain.RequestScope;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenShiftCreateRequest(
		@NotNull(message = "시작 시간을 입력해 주세요.") LocalDateTime startAt,
		@NotNull(message = "종료 시간을 입력해 주세요.") LocalDateTime endAt,
		@NotNull(message = "시간대를 입력해 주세요.")
		@Size(max = 10, message = "시간대는 10자 이내로 입력해 주세요.") String position,
		RequestScope scope,
		@Size(max = 100, message = "요청 메시지는 100자 이내로 입력해 주세요.") String message,
		@NotEmpty(message = "알림을 보낼 직원을 한 명 이상 선택해 주세요.") List<@NotNull Long> notifyUserIds
) {
}
