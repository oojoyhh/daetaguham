package com.daetaguham.store.api;

import com.daetaguham.store.domain.MemberStatus;

public enum MemberDecision {
	ACTIVE,
	REJECTED;

	public MemberStatus toStatus() {
		return MemberStatus.valueOf(name());
	}
}
