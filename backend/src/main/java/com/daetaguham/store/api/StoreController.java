package com.daetaguham.store.api;

import com.daetaguham.store.application.StoreService;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores")
public class StoreController {

	private final StoreService storeService;

	public StoreController(StoreService storeService) {
		this.storeService = storeService;
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
}
