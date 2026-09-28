package com.daetaguham.request.api;

import java.time.LocalDate;

public record ApprovalChangeResponse(
		LocalDate date,
		ApprovalShiftResponse before,
		ApprovalShiftResponse after
) {
}
