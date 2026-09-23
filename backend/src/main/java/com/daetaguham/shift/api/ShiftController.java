package com.daetaguham.shift.api;

import java.time.LocalDate;
import java.util.List;

import com.daetaguham.shift.application.ShiftService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShiftController {

	private final ShiftService shiftService;

	public ShiftController(ShiftService shiftService) {
		this.shiftService = shiftService;
	}

	@GetMapping("/stores/{storeId}/shift-templates")
	public List<ShiftTemplateResponse> templates(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId
	) {
		return shiftService.findTemplates(Long.valueOf(jwt.getSubject()), storeId).stream()
				.map(ShiftTemplateResponse::from)
				.toList();
	}

	@PutMapping("/stores/{storeId}/shift-templates")
	public List<ShiftTemplateResponse> replaceTemplates(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@Valid @RequestBody List<@Valid ShiftTemplateWriteRequest> requests
	) {
		return shiftService.replaceTemplates(
				Long.valueOf(jwt.getSubject()),
				storeId,
				requests.stream().map(ShiftTemplateWriteRequest::toCommand).toList()
		).stream().map(ShiftTemplateResponse::from).toList();
	}

	@GetMapping("/stores/{storeId}/shifts")
	public List<ShiftResponse> shifts(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return shiftService.findShifts(Long.valueOf(jwt.getSubject()), storeId, from, to).stream()
				.map(ShiftResponse::from)
				.toList();
	}

	@PostMapping("/stores/{storeId}/shifts")
	@ResponseStatus(HttpStatus.CREATED)
	public ShiftResponse createShift(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long storeId,
			@Valid @RequestBody ShiftWriteRequest request
	) {
		return ShiftResponse.from(shiftService.createShift(
				Long.valueOf(jwt.getSubject()),
				storeId,
				request.workerId(),
				request.startAt(),
				request.endAt(),
				request.position()
		));
	}

	@PutMapping("/shifts/{shiftId}")
	public ShiftResponse updateShift(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long shiftId,
			@Valid @RequestBody ShiftWriteRequest request
	) {
		return ShiftResponse.from(shiftService.updateShift(
				Long.valueOf(jwt.getSubject()),
				shiftId,
				request.workerId(),
				request.startAt(),
				request.endAt(),
				request.position()
		));
	}

	@DeleteMapping("/shifts/{shiftId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteShift(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long shiftId
	) {
		shiftService.deleteShift(Long.valueOf(jwt.getSubject()), shiftId);
	}
}
