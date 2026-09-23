package com.daetaguham.user.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvailabilityWriteRequest(
		@NotNull(message = "근무 가능 시간 목록을 보내 주세요.")
		@Size(max = 7, message = "근무 가능 시간은 요일별로 하나씩만 저장할 수 있어요.")
		List<@Valid AvailabilityItem> items
) {
}
