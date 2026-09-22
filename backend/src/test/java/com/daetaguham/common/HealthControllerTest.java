package com.daetaguham.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

	private final HealthController controller = new HealthController();

	@Test
	void returnsServiceHealth() {
		HealthController.HealthResponse response = controller.health();

		assertEquals("ok", response.status());
		assertEquals("daetaguham-api", response.service());
	}
}
