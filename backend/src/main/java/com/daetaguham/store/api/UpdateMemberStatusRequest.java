package com.daetaguham.store.api;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberStatusRequest(
		@NotNull(message = "처리할 상태를 선택해 주세요.")
		MemberDecision status
) {
}
