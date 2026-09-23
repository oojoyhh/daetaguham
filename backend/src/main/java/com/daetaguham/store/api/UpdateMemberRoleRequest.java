package com.daetaguham.store.api;

import com.daetaguham.store.domain.MemberRole;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
		@NotNull(message = "직원 역할을 선택해 주세요.")
		MemberRole role
) {
}
