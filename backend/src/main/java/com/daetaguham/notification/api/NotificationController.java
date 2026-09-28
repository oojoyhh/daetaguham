package com.daetaguham.notification.api;

import com.daetaguham.notification.application.NotificationService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public NotificationPageResponse findMine(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "false") boolean unreadOnly,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int size
	) {
		return NotificationPageResponse.from(notificationService.findMine(
				Long.valueOf(jwt.getSubject()), unreadOnly, page, size));
	}

	@PutMapping("/{notificationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markRead(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long notificationId
	) {
		notificationService.markRead(Long.valueOf(jwt.getSubject()), notificationId);
	}

	@PutMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
		notificationService.markAllRead(Long.valueOf(jwt.getSubject()));
	}
}
