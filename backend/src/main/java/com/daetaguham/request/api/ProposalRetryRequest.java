package com.daetaguham.request.api;

import java.time.LocalDate;
import java.util.List;

import com.daetaguham.request.domain.RequestScope;

public record ProposalRetryRequest(
		Long targetUserId,
		List<Long> targetShiftIds,
		List<LocalDate> availableDates,
		boolean switchToPublic,
		RequestScope scope
) {
}
