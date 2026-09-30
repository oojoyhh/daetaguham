package com.daetaguham.store.api;

import jakarta.validation.constraints.NotNull;

public record StoreSettingsRequest(
		@NotNull(message = "승인 규칙을 선택해 주세요.") Boolean approvalRequired
) {
}
