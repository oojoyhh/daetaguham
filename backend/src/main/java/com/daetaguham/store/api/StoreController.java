package com.daetaguham.store.api;

import java.util.List;

import com.daetaguham.store.application.StoreMemberManagementService;
import com.daetaguham.store.application.StoreService;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores")
public class StoreController {

	private final StoreService storeService;
	private final StoreMemberManagementService memberManagementService;

	public StoreController(StoreService storeService, StoreMemberManagementService memberManagementService) {
		this.storeService = storeService;
		this.memberManagementService = memberManagementService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public StoreResponse create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateStoreRequest request
	) {
		Store store = storeService.create(
				Long.valueOf(jwt.getSubject()),
				request.name(),
				request.category(),
				request.address()
		);
		return StoreResponse.from(store);
	}

	@PostMapping("/join")
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponse join(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody JoinStoreRequest request
	) {
		StoreMember membership = storeService.join(
				Long.valueOf(jwt.getSubject()),
				request.inviteCode()
		);
		return MemberResponse.from(membership);
	}

	@GetMapping("/{storeId}/members")
	public List<MemberResponse> members(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@RequestParam(required = false) MemberStatus status
	) {
		return memberManagementService.findMembers(Long.valueOf(jwt.getSubject()), storeId, status).stream()
				.map(MemberResponse::from)
				.toList();
	}

	@PutMapping("/{storeId}/members/{userId}/status")
	public MemberResponse updateStatus(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@PathVariable Long userId,
			@Valid @RequestBody UpdateMemberStatusRequest request
	) {
		StoreMember membership = memberManagementService.decideStatus(
				Long.valueOf(jwt.getSubject()),
				storeId,
				userId,
				request.status().toStatus()
		);
		return MemberResponse.from(membership);
	}

	@PutMapping("/{storeId}/members/{userId}/role")
	public MemberResponse updateRole(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@PathVariable Long userId,
			@Valid @RequestBody UpdateMemberRoleRequest request
	) {
		StoreMember membership = memberManagementService.changeRole(
				Long.valueOf(jwt.getSubject()),
				storeId,
				userId,
				request.role()
		);
		return MemberResponse.from(membership);
	}
}
