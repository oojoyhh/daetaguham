package com.daetaguham.store.api;

import java.util.List;

import com.daetaguham.store.application.StoreNoticeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreNoticeController {

	private final StoreNoticeService storeNoticeService;

	public StoreNoticeController(StoreNoticeService storeNoticeService) {
		this.storeNoticeService = storeNoticeService;
	}

	@GetMapping("/stores/{storeId}/notices")
	public List<StoreNoticeResponse> notices(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId
	) {
		return storeNoticeService.findByStore(Long.valueOf(jwt.getSubject()), storeId).stream()
				.map(StoreNoticeResponse::from)
				.toList();
	}

	@PostMapping("/stores/{storeId}/notices")
	@ResponseStatus(HttpStatus.CREATED)
	public StoreNoticeResponse create(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@Valid @RequestBody StoreNoticeWriteRequest request
	) {
		return StoreNoticeResponse.from(storeNoticeService.create(
				Long.valueOf(jwt.getSubject()),
				storeId,
				request.noticeDate(),
				request.content()
		));
	}

	@DeleteMapping("/notices/{noticeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long noticeId
	) {
		storeNoticeService.delete(Long.valueOf(jwt.getSubject()), noticeId);
	}
}
