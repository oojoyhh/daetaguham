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
	private final com.daetaguham.request.application.ApplicationService applicationService;
	private final com.daetaguham.request.application.RequestResolutionService resolutionService;

	public RequestController(
			RequestService requestService,
			com.daetaguham.request.application.ApplicationService applicationService,
			com.daetaguham.request.application.RequestResolutionService resolutionService
	) {
		this.requestService = requestService;
		this.applicationService = applicationService;
		this.resolutionService = resolutionService;
	}

	@PostMapping("/requests/{requestId}/applications")
	@ResponseStatus(HttpStatus.CREATED)
	public ApplicationResponse apply(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long requestId,
			@Valid @RequestBody(required = false) ApplicationWriteRequest request
	) {
		ApplicationWriteRequest body = request == null
				? new ApplicationWriteRequest(null, null)
				: request;
		return ApplicationResponse.from(applicationService.apply(
				Long.valueOf(jwt.getSubject()),
				requestId,
				body.offeredShiftIds(),
				body.message()
		));
	}

	@PutMapping("/applications/{applicationId}/withdraw")
	public ApplicationResponse withdraw(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long applicationId
	) {
		return ApplicationResponse.from(applicationService.withdraw(
				Long.valueOf(jwt.getSubject()), applicationId));
	}

	@PutMapping("/applications/{applicationId}/response")
	public RequestDetailResponse respond(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long applicationId,
			@Valid @RequestBody ProposalResponseRequest request
	) {
		Long actorId = Long.valueOf(jwt.getSubject());
		Long requestId = applicationService.respondToProposal(
				actorId, applicationId, request.accept(), request.offerShiftId());
		return RequestDetailResponse.from(requestService.find(actorId, requestId));
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

	@PostMapping("/requests/{requestId}/proposals")
	public RequestDetailResponse retryProposal(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long requestId,
			@Valid @RequestBody ProposalRetryRequest request
	) {
		return RequestDetailResponse.from(requestService.retryProposal(
				Long.valueOf(jwt.getSubject()),
				requestId,
				request.targetUserId(),
				request.targetShiftIds(),
				request.availableDates(),
				request.switchToPublic(),
				request.scope()
		));
	}

	@PutMapping("/requests/{requestId}/selection")
	public RequestDetailResponse selectApplication(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long requestId,
			@Valid @RequestBody SelectionRequest request
	) {
		Long actorId = Long.valueOf(jwt.getSubject());
		resolutionService.select(
				actorId, requestId, request.applicationId(), request.offerShiftId());
		return RequestDetailResponse.from(requestService.find(actorId, requestId));
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
