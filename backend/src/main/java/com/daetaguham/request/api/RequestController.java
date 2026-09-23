package com.daetaguham.request.api;

import com.daetaguham.request.application.RequestService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestController {

	private final RequestService requestService;

	public RequestController(RequestService requestService) {
		this.requestService = requestService;
	}

	@PostMapping("/requests")
	@ResponseStatus(HttpStatus.CREATED)
	public RequestDetailResponse create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody RequestCreateRequest request
	) {
		return RequestDetailResponse.from(requestService.createPublic(
				Long.valueOf(jwt.getSubject()),
				request.type(),
				request.shiftId(),
				request.mode(),
				request.scope(),
				request.targetUserId(),
				request.targetShiftIds(),
				request.availableDates(),
				request.reason()
		));
	}

	@GetMapping("/requests/{requestId}")
	public RequestDetailResponse detail(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long requestId
	) {
		return RequestDetailResponse.from(requestService.find(
				Long.valueOf(jwt.getSubject()), requestId));
	}

	@PutMapping("/requests/{requestId}/cancel")
	public RequestDetailResponse cancel(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long requestId
	) {
		return RequestDetailResponse.from(requestService.cancel(
				Long.valueOf(jwt.getSubject()), requestId));
	}

	@PostMapping("/stores/{storeId}/open-shifts")
	@ResponseStatus(HttpStatus.CREATED)
	public OpenShiftCreateResponse createOpenShift(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@Valid @RequestBody OpenShiftCreateRequest request
	) {
		return OpenShiftCreateResponse.from(requestService.createOpenShift(
				Long.valueOf(jwt.getSubject()),
				storeId,
				request.startAt(),
				request.endAt(),
				request.position(),
				request.scope(),
				request.message(),
				request.notifyUserIds()
		));
	}
}
