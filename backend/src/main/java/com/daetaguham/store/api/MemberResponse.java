package com.daetaguham.store.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.StoreMember;

public record MemberResponse(
		Long userId,
		String name,
		String phone,
		MemberRole role,
		MemberStatus status,
		LocalDateTime requestedAt,
		LocalDateTime joinedAt,
		BigDecimal monthHours,
		int monthShiftCount,
		int upcomingShiftCount,
		int coverCount
) {
	public static MemberResponse from(StoreMember membership) {
		return new MemberResponse(
				membership.getUser().getId(),
				membership.getUser().getName(),
				membership.getUser().getPhone(),
				membership.getRole(),
				membership.getStatus(),
				membership.getRequestedAt(),
				membership.getJoinedAt(),
				BigDecimal.ZERO,
				0,
				0,
				0
		);
	}
}
