package com.daetaguham.shift.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.daetaguham.request.application.ShiftRequestForbiddenException;
import com.daetaguham.request.domain.RequestScope;
import com.daetaguham.request.domain.RequestStatus;
import com.daetaguham.request.domain.RequestType;
import com.daetaguham.request.domain.ShiftRequestRepository;
import com.daetaguham.shift.application.CandidateReasonGenerator.CandidateReasonContext;
import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.store.application.OwnerPermissionRequiredException;
import com.daetaguham.store.application.StoreNotFoundException;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.Availability;
import com.daetaguham.user.domain.AvailabilityRepository;
import com.daetaguham.user.domain.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateRecommendationService {

	private static final java.util.Set<RequestType> GIVE_TAKE_TYPES =
			EnumSet.of(RequestType.COVER, RequestType.EXCHANGE);

	private final ShiftRepository shiftRepository;
	private final ShiftRequestRepository requestRepository;
	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final AvailabilityRepository availabilityRepository;
	private final CandidateReasonGenerator reasonGenerator;

	public CandidateRecommendationService(
			ShiftRepository shiftRepository,
			ShiftRequestRepository requestRepository,
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			AvailabilityRepository availabilityRepository,
			CandidateReasonGenerator reasonGenerator
	) {
		this.shiftRepository = shiftRepository;
		this.requestRepository = requestRepository;
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.availabilityRepository = availabilityRepository;
		this.reasonGenerator = reasonGenerator;
	}

	@Transactional(readOnly = true)
	public CandidateResult findForShift(
			Long actorId,
			Long shiftId,
			RequestType type,
			RequestScope scope,
			int limit
	) {
		Shift shift = shiftRepository.findById(shiftId).orElseThrow(ShiftNotFoundException::new);
		if (shift.getWorker() == null || !shift.getWorker().getId().equals(actorId)) {
			throw new ShiftRequestForbiddenException();
		}
		if (type != RequestType.COVER && type != RequestType.EXCHANGE) {
			throw new InvalidShiftException("대타 또는 교대 유형만 추천할 수 있어요.");
		}
		if (limit < 1 || limit > 20) {
			throw new InvalidShiftException("추천 인원은 1~20명 사이여야 해요.");
		}
		RequestScope resolvedScope = scope == null ? RequestScope.STORE : scope;
		if (type == RequestType.EXCHANGE && resolvedScope != RequestScope.STORE) {
			throw new InvalidShiftException("교대 후보는 같은 매장에서만 추천할 수 있어요.");
		}
		validateTargetTime(shift.getStartAt(), shift.getEndAt());
		return recommend(
				shift.getStore(),
				shift.getStartAt(),
				shift.getEndAt(),
				resolvedScope,
				type,
				shift.getWorker(),
				shift,
				limit
		);
	}

	@Transactional(readOnly = true)
	public CandidateResult findForOpenShift(
			Long actorId,
			Long storeId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			RequestScope scope
	) {
		Store store = storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
		if (!store.getOwner().getId().equals(actorId)) {
			throw new OwnerPermissionRequiredException();
		}
		if (scope == null) {
			throw new InvalidShiftException("급구 추천 범위를 선택해 주세요.");
		}
		validateTargetTime(startAt, endAt);
		return recommend(
				store,
				startAt,
				endAt,
				scope,
				RequestType.OPEN_SHIFT,
				null,
				null,
				Integer.MAX_VALUE
		);
	}

	private CandidateResult recommend(
			Store store,
			LocalDateTime startAt,
			LocalDateTime endAt,
			RequestScope scope,
			RequestType type,
			User requester,
			Shift requestedShift,
			int limit
	) {
		List<StoreMember> memberships = scope == RequestScope.STORE
				? storeMemberRepository.findAllByStore_IdAndStatusOrderByRequestedAtAsc(
						store.getId(), MemberStatus.ACTIVE)
				: storeMemberRepository.findAllByStatusAndStore_Owner_Id(
						MemberStatus.ACTIVE, store.getOwner().getId());
		Map<Long, CandidateSeed> seeds = candidateSeeds(
				memberships, store, requester == null ? null : requester.getId());
		Map<Long, Availability> availabilities = availabilityMap(seeds.keySet(), startAt);
		List<CandidateView> eligible = new ArrayList<>();

		for (CandidateSeed seed : seeds.values()) {
			User user = seed.user();
			Availability availability = availabilities.get(user.getId());
			if (!matchesAvailability(availability, startAt, endAt)) {
				continue;
			}
			if (!shiftRepository.findOverlappingShifts(user.getId(), startAt, endAt).isEmpty()) {
				continue;
			}

			List<Shift> exchangeShifts = List.of();
			if (type == RequestType.EXCHANGE) {
				exchangeShifts = findExchangeShifts(
						user.getId(), store.getId(), requester.getId(), requestedShift);
				if (exchangeShifts.isEmpty()) {
					continue;
				}
			}

			double weekHours = calculateWeekHours(user.getId(), startAt.toLocalDate());
			int gaveToMe = requester == null ? 0 : Math.toIntExact(
					requestRepository.countByRequester_IdAndTypeInAndStatusAndShift_Worker_Id(
							requester.getId(), GIVE_TAKE_TYPES, RequestStatus.CONFIRMED, user.getId()));
			int recentOpenShifts = type == RequestType.OPEN_SHIFT ? Math.toIntExact(
					requestRepository.countByTypeAndStatusAndShift_Worker_IdAndConfirmedAtGreaterThanEqual(
							RequestType.OPEN_SHIFT,
							RequestStatus.CONFIRMED,
							user.getId(),
							LocalDateTime.now().minusDays(30))) : 0;
			int score = score(seed.sameStore(), weekHours, gaveToMe, recentOpenShifts, type);
			String reason = reasonGenerator.generate(new CandidateReasonContext(
					seed.storeName(),
					seed.sameStore(),
					weekHours,
					gaveToMe,
					recentOpenShifts,
					dayLabel(startAt.getDayOfWeek()),
					timeLabel(startAt.toLocalTime(), endAt.toLocalTime()),
					type == RequestType.OPEN_SHIFT
			));
			eligible.add(new CandidateView(
					user, seed.storeName(), score, weekHours, gaveToMe, reason, exchangeShifts));
		}

		eligible.sort(Comparator
				.comparingInt(CandidateView::score).reversed()
				.thenComparingDouble(CandidateView::weekHours)
				.thenComparing(candidate -> candidate.user().getName())
				.thenComparing(candidate -> candidate.user().getId()));
		int excludedCount = seeds.size() - eligible.size();
		return new CandidateResult(
				eligible.stream().limit(limit).toList(), excludedCount);
	}

	private Map<Long, CandidateSeed> candidateSeeds(
			Collection<StoreMember> memberships,
			Store targetStore,
			Long excludedUserId
	) {
		Map<Long, List<StoreMember>> membershipsByUser = memberships.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						member -> member.getUser().getId(), LinkedHashMap::new,
						java.util.stream.Collectors.toList()));
		Map<Long, CandidateSeed> result = new LinkedHashMap<>();
		for (List<StoreMember> userMemberships : membershipsByUser.values()) {
			User user = userMemberships.getFirst().getUser();
			if ((excludedUserId != null && user.getId().equals(excludedUserId))
					|| storeRepository.existsByOwner_Id(user.getId())) {
				continue;
			}
			StoreMember selected = userMemberships.stream()
					.sorted(Comparator
							.comparing((StoreMember member) -> !member.getStore().getId().equals(targetStore.getId()))
							.thenComparing(member -> member.getStore().getId()))
					.findFirst()
					.orElseThrow();
			boolean sameStore = selected.getStore().getId().equals(targetStore.getId());
			result.put(user.getId(), new CandidateSeed(
					user, selected.getStore().getName(), sameStore));
		}
		return result;
	}

	private Map<Long, Availability> availabilityMap(
			Collection<Long> userIds,
			LocalDateTime startAt
	) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		short dayOfWeek = (short) (startAt.getDayOfWeek().getValue() % 7);
		return availabilityRepository.findAllByUser_IdInAndDayOfWeek(userIds, dayOfWeek).stream()
				.collect(java.util.stream.Collectors.toMap(
						availability -> availability.getUser().getId(),
						availability -> availability));
	}

	private boolean matchesAvailability(
			Availability availability,
			LocalDateTime startAt,
			LocalDateTime endAt
	) {
		return availability != null
				&& startAt.toLocalDate().equals(endAt.toLocalDate())
				&& !availability.getStartTime().isAfter(startAt.toLocalTime())
				&& !availability.getEndTime().isBefore(endAt.toLocalTime());
	}

	private List<Shift> findExchangeShifts(
			Long candidateId,
			Long storeId,
			Long requesterId,
			Shift requestedShift
	) {
		return shiftRepository
				.findAllByWorker_IdAndStore_IdAndStartAtAfterOrderByStartAtAsc(
						candidateId, storeId, LocalDateTime.now()).stream()
				.filter(shift -> !shiftRepository.existsOverlappingShift(
						requesterId,
						shift.getStartAt(),
						shift.getEndAt(),
						requestedShift.getId()))
				.toList();
	}

	private double calculateWeekHours(Long userId, LocalDate targetDate) {
		LocalDate monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return shiftRepository
				.findAllByWorker_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
						userId, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay())
				.stream()
				.mapToLong(shift -> Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes())
				.sum() / 60.0;
	}

	private int score(
			boolean sameStore,
			double weekHours,
			int gaveToMe,
			int recentOpenShifts,
			RequestType type
	) {
		int storePoints = sameStore ? 20 : 10;
		int workloadPoints;
		if (weekHours <= 6) {
			workloadPoints = 30;
		} else if (weekHours <= 12) {
			workloadPoints = 24;
		} else if (weekHours <= 20) {
			workloadPoints = 16;
		} else if (weekHours <= 30) {
			workloadPoints = 8;
		} else {
			workloadPoints = 0;
		}
		int finalPoints = type == RequestType.OPEN_SHIFT
				? Math.max(0, 10 - recentOpenShifts * 3)
				: Math.min(10, gaveToMe * 2);
		return Math.min(100, 40 + storePoints + workloadPoints + finalPoints);
	}

	private void validateTargetTime(LocalDateTime startAt, LocalDateTime endAt) {
		if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
			throw new InvalidShiftException("추천할 근무의 시작·종료 시간을 확인해 주세요.");
		}
		if (!startAt.isAfter(LocalDateTime.now())) {
			throw new InvalidShiftException("앞으로 예정된 근무만 후보를 추천할 수 있어요.");
		}
	}

	private String dayLabel(DayOfWeek dayOfWeek) {
		return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN);
	}

	private String timeLabel(LocalTime start, LocalTime end) {
		return start + "–" + end;
	}

	private record CandidateSeed(User user, String storeName, boolean sameStore) {
	}

	public record CandidateView(
			User user,
			String storeName,
			int score,
			double weekHours,
			int gaveToMe,
			String aiReason,
			List<Shift> shifts
	) {
	}

	public record CandidateResult(List<CandidateView> candidates, int excludedCount) {
	}
}
