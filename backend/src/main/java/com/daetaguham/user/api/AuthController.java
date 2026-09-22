package com.daetaguham.user.api;

import com.daetaguham.user.application.UserAccountService;
import com.daetaguham.user.application.UserAccountService.LoginResult;
import com.daetaguham.user.application.UserSignupService;
import com.daetaguham.user.domain.User;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final UserSignupService userSignupService;
	private final UserAccountService userAccountService;

	public AuthController(UserSignupService userSignupService, UserAccountService userAccountService) {
		this.userSignupService = userSignupService;
		this.userAccountService = userAccountService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse signup(@Valid @RequestBody SignupRequest request) {
		User user = userSignupService.signup(request.name(), request.phone(), request.password());
		return UserResponse.from(user);
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = userAccountService.login(request.phone(), request.password());
		return LoginResponse.from(result);
	}
}
