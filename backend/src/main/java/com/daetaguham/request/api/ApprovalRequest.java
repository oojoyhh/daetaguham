package com.daetaguham.request.api;

import com.daetaguham.request.application.RequestResolutionService.ApprovalDecision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalRequest(
		@NotNull(message = "승인 또는 반려를 선택해 주세요.") ApprovalDecision decision,
		@Size(max = 200, message = "반려 사유는 200자 이내로 입력해 주세요.") String comment
) {
}
