package com.daetaguham.request.application;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RequestExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(RequestExpirationScheduler.class);

	private final RequestExpirationService expirationService;

	public RequestExpirationScheduler(RequestExpirationService expirationService) {
		this.expirationService = expirationService;
	}

	@Scheduled(
			initialDelayString = "${app.requests.expiration.initial-delay:PT30S}",
			fixedDelayString = "${app.requests.expiration.fixed-delay:PT1M}"
	)
	public void expireDueRequests() {
		int expiredCount = expirationService.expireDueRequests(LocalDateTime.now());
		if (expiredCount > 0) {
			log.info("Expired {} shift request(s)", expiredCount);
		}
	}
}
