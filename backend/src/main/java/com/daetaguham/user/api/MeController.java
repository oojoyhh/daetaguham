package com.daetaguham.user.api;

import java.time.LocalDate;
import java.util.List;

import com.daetaguham.shift.application.ShiftService;
import com.daetaguham.store.api.StoreNoticeResponse;
import com.daetaguham.store.application.StoreNoticeService;
import com.daetaguham.user.application.AvailabilityService;
import com.daetaguham.user.application.UserAccountService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MeController {

	private final UserAccountService userAccountService;
	private final ShiftService shiftService;
	private final AvailabilityService availabilityService;
	private final StoreNoticeService storeNoticeService;

	public MeController(
			UserAccountService userAccountService,
			ShiftService shiftService,
			AvailabilityService availabilityService,
			StoreNoticeService storeNoticeService
	) {
		this.userAccountService = userAccountService;
		this.shiftService = shiftService;
		this.availabilityService = availabilityService;
		this.storeNoticeService = storeNoticeService;
	}

	@GetMapping
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		Long userId = Long.valueOf(jwt.getSubject());
		return MeResponse.from(userAccountService.getAccount(userId));
	}

	@GetMapping("/shifts")
	public List<MyShiftResponse> shifts(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return shiftService.findMyShifts(Long.valueOf(jwt.getSubject()), from, to).stream()
				.map(MyShiftResponse::from)
				.toList();
	}

	@GetMapping("/availabilities")
	public List<AvailabilityItem> availabilities(@AuthenticationPrincipal Jwt jwt) {
		return availabilityService.findMine(Long.valueOf(jwt.getSubject())).stream()
				.map(AvailabilityItem::from)
				.toList();
	}

	@PutMapping("/availabilities")
	public List<AvailabilityItem> replaceAvailabilities(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody AvailabilityWriteRequest request
	) {
		return availabilityService.replaceMine(
				Long.valueOf(jwt.getSubject()),
				request.items().stream().map(AvailabilityItem::toCommand).toList()
		).stream().map(AvailabilityItem::from).toList();
	}

	@GetMapping("/store-notices")
	public List<StoreNoticeResponse> storeNotices(@AuthenticationPrincipal Jwt jwt) {
		return storeNoticeService.findMine(Long.valueOf(jwt.getSubject())).stream()
				.map(StoreNoticeResponse::from)
				.toList();
	}
}
