package com.daetaguham.common.error;

import com.daetaguham.user.application.DuplicatePhoneException;
import com.daetaguham.user.application.InvalidCredentialsException;
import com.daetaguham.store.application.ExistingMembershipException;
import com.daetaguham.store.application.OwnerCannotJoinException;
import com.daetaguham.store.application.StoreNotFoundException;

import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("입력값을 확인해 주세요.");
		return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class})
	ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값을 확인해 주세요.");
	}

	@ExceptionHandler(DuplicatePhoneException.class)
	ResponseEntity<ApiErrorResponse> handleDuplicatePhone(DuplicatePhoneException exception) {
		return error(HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS", exception.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage());
	}

	@ExceptionHandler(StoreNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleStoreNotFound(StoreNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(OwnerCannotJoinException.class)
	ResponseEntity<ApiErrorResponse> handleOwnerCannotJoin(OwnerCannotJoinException exception) {
		return error(HttpStatus.CONFLICT, "OWNER_CANNOT_JOIN", exception.getMessage());
	}

	@ExceptionHandler(ExistingMembershipException.class)
	ResponseEntity<ApiErrorResponse> handleExistingMembership(ExistingMembershipException exception) {
		return error(HttpStatus.CONFLICT, "MEMBERSHIP_ALREADY_EXISTS", exception.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiErrorResponse> handleDataConflict(DataIntegrityViolationException exception) {
		return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "이미 사용 중인 값입니다.");
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String errorCode, String message) {
		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(status.value(), errorCode, message));
	}
}
