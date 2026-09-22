package com.daetaguham.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.daetaguham.common.error.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요하거나 토큰이 유효하지 않아요.");
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException exception
	) throws IOException {
		write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "이 작업을 할 권한이 없어요.");
	}

	private void write(HttpServletResponse response, HttpStatus status, String errorCode, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(
				response.getOutputStream(),
				new ApiErrorResponse(status.value(), errorCode, message)
		);
	}
}
