package com.daetaguham.store.api;

import com.daetaguham.store.application.StoreOverviewService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreOverviewController {

	private final StoreOverviewService storeOverviewService;

	public StoreOverviewController(StoreOverviewService storeOverviewService) {
		this.storeOverviewService = storeOverviewService;
	}

	@GetMapping("/stores/{storeId}/summary")
	public StoreOverviewResponse summary(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId
	) {
		return StoreOverviewResponse.from(storeOverviewService.today(
				Long.valueOf(jwt.getSubject()), storeId));
	}
}
