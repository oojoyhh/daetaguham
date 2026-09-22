package com.daetaguham.user.api;

import com.daetaguham.user.application.UserAccountService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MeController {

	private final UserAccountService userAccountService;

	public MeController(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@GetMapping
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		Long userId = Long.valueOf(jwt.getSubject());
		return MeResponse.from(userAccountService.getAccount(userId));
	}
}
