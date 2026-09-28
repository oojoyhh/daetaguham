package com.daetaguham.request.api;

import java.util.List;

import com.daetaguham.request.application.ApplicationService;
import com.daetaguham.request.application.RequestResolutionService;
import com.daetaguham.request.application.RequestService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApprovalController {

	private final RequestResolutionService resolutionService;
	private final RequestService requestService;
	private final ApplicationService applicationService;

	public ApprovalController(
			RequestResolutionService resolutionService,
			RequestService requestService,
			ApplicationService applicationService
	) {
		this.resolutionService = resolutionService;
		this.requestService = requestService;
		this.applicationService = applicationService;
	}

	@GetMapping("/stores/{storeId}/approvals")
	public List<ApprovalItemResponse> approvals(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId
	) {
		Long actorId = Long.valueOf(jwt.getSubject());
		return resolutionService.findPendingApprovals(actorId, storeId).stream()
				.map(pending -> ApprovalItemResponse.from(
						pending,
						RequestDetailResponse.from(requestService.find(actorId, pending.request().getId())),
						ApplicationResponse.from(applicationService.findView(
								pending.selectedApplication().getId()))
				))
				.toList();
	}

	@PutMapping("/requests/{requestId}/approval")
	public RequestDetailResponse decide(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long requestId,
			@Valid @RequestBody ApprovalRequest request
	) {
		Long actorId = Long.valueOf(jwt.getSubject());
		resolutionService.decideApproval(actorId, requestId, request.decision(), request.comment());
		return RequestDetailResponse.from(requestService.find(actorId, requestId));
	}
}
