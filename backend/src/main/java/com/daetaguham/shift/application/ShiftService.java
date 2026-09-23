package com.daetaguham.shift.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.daetaguham.shift.domain.Shift;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.shift.domain.ShiftTemplate;
import com.daetaguham.shift.domain.ShiftTemplateRepository;
import com.daetaguham.store.application.StoreManagementForbiddenException;
import com.daetaguham.store.application.StoreNotFoundException;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.application.InvalidCredentialsException;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ShiftService {

	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final UserRepository userRepository;
	private final ShiftTemplateRepository shiftTemplateRepository;
	private final ShiftRepository shiftRepository;

	public ShiftService(
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			UserRepository userRepository,
			ShiftTemplateRepository shiftTemplateRepository,
			ShiftRepository shiftRepository
	) {
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.userRepository = userRepository;
		this.shiftTemplateRepository = shiftTemplateRepository;
		this.shiftRepository = shiftRepository;
	}

	@Transactional(readOnly = true)
	public List<ShiftTemplate> findTemplates(Long actorId, Long storeId) {
		Store store = findStore(storeId);
		requireStoreAccess(actorId, store);
		return shiftTemplateRepository.findAllByStore_IdOrderByStartTimeAsc(storeId);
	}

	@Transactional
	public List<ShiftTemplate> replaceTemplates(
			Long actorId,
			Long storeId,
			List<TemplateCommand> commands
	) {
		Store store = findStore(storeId);
		requireStoreManagement(actorId, store);
		validateTemplates(commands);

		Map<String, ShiftTemplate> existingByName = new HashMap<>();
		for (ShiftTemplate template : shiftTemplateRepository.findAllByStore_IdOrderByStartTimeAsc(storeId)) {
			existingByName.put(template.getName(), template);
		}

		List<ShiftTemplate> replacements = new ArrayList<>();
		for (TemplateCommand command : commands) {
			String name = command.name().trim();
			ShiftTemplate template = existingByName.remove(name);
			if (template == null) {
				template = ShiftTemplate.create(
						store,
						name,
						command.startTime(),
						command.endTime(),
						command.requiredCount()
				);
			} else {
				template.update(name, command.startTime(), command.endTime(), command.requiredCount());
			}
			replacements.add(template);
		}

		shiftTemplateRepository.deleteAll(existingByName.values());
		shiftTemplateRepository.flush();
		List<ShiftTemplate> saved = shiftTemplateRepository.saveAll(replacements);
		saved.sort(Comparator.comparing(ShiftTemplate::getStartTime));
		return saved;
	}

	@Transactional(readOnly = true)
	public List<ManagedShift> findShifts(Long actorId, Long storeId, LocalDate from, LocalDate to) {
		Store store = findStore(storeId);
		requireStoreAccess(actorId, store);
		if (from.isAfter(to)) {
			throw new InvalidShiftException("조회 시작일은 종료일보다 늦을 수 없어요.");
		}
		return shiftRepository
				.findAllByStore_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
						storeId,
						from.atStartOfDay(),
						to.plusDays(1).atStartOfDay()
				)
				.stream()
				.map(this::toManagedShift)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<MyShift> findMyShifts(Long userId, LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw new InvalidShiftException("조회 시작일은 종료일보다 늦을 수 없어요.");
		}
		LocalDateTime now = LocalDateTime.now();
		return shiftRepository
				.findAllByWorker_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
						userId,
						from.atStartOfDay(),
						to.plusDays(1).atStartOfDay()
				)
				.stream()
				.map(shift -> new MyShift(
						shift,
						toManagedShift(shift).helper(),
						progressOf(shift, now)
				))
				.toList();
	}

	@Transactional
	public BulkSaveResult saveBulk(
			Long actorId,
			Long storeId,
			LocalDate from,
			LocalDate to,
			List<BulkItemCommand> commands
	) {
		Store store = findStore(storeId);
		requireStoreManagement(actorId, store);
		if (from.isAfter(to)) {
			throw new InvalidShiftException("저장 시작일은 종료일보다 늦을 수 없어요.");
		}

		Map<String, ShiftTemplate> templates = new HashMap<>();
		for (ShiftTemplate template : shiftTemplateRepository.findAllByStore_IdOrderByStartTimeAsc(storeId)) {
			templates.put(template.getName(), template);
		}

		List<Shift> existing = shiftRepository
				.findAllByStore_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
						storeId,
						from.atStartOfDay(),
						to.plusDays(1).atStartOfDay()
				);
		Set<Long> replaceableIds = existing.stream().map(Shift::getId).collect(java.util.stream.Collectors.toSet());

		List<PreparedShift> prepared = new ArrayList<>();
		Map<Long, User> workers = new HashMap<>();
		for (BulkItemCommand command : commands) {
			if (command.date().isBefore(from) || command.date().isAfter(to)) {
				throw new InvalidShiftException("근무 날짜는 저장 기간 안에 있어야 해요.");
			}
			String position = command.position().trim();
			ShiftTemplate template = templates.get(position);
			if (template == null) {
				throw new InvalidShiftException("매장에 없는 시간대예요: " + position);
			}
			User worker = null;
			if (command.workerId() != null) {
				worker = workers.computeIfAbsent(
						command.workerId(),
						workerId -> findEligibleWorker(store, workerId)
				);
			}
			prepared.add(new PreparedShift(
					worker,
					command.date().atTime(template.getStartTime()),
					command.date().atTime(template.getEndTime()),
					position
			));
		}

		List<ShiftBulkConflictException.Conflict> conflicts = findBulkConflicts(
				store, prepared, replaceableIds);
		if (!conflicts.isEmpty()) {
			throw new ShiftBulkConflictException(conflicts);
		}

		Map<ShiftKey, Deque<Shift>> existingByKey = new HashMap<>();
		for (Shift shift : existing) {
			existingByKey.computeIfAbsent(ShiftKey.from(shift), ignored -> new ArrayDeque<>()).add(shift);
		}

		User actor = userRepository.findById(actorId).orElseThrow(InvalidCredentialsException::new);
		List<Shift> retained = new ArrayList<>();
		List<Shift> toCreate = new ArrayList<>();
		for (PreparedShift item : prepared) {
			Deque<Shift> matches = existingByKey.get(ShiftKey.from(item));
			Shift matched = matches == null ? null : matches.pollFirst();
			if (matched != null) {
				retained.add(matched);
			} else {
				toCreate.add(Shift.create(
						store,
						item.worker(),
						item.startAt(),
						item.endAt(),
						item.position(),
						actor
				));
			}
		}

		List<Shift> toDelete = existingByKey.values().stream().flatMap(Deque::stream).toList();
		shiftRepository.deleteAll(toDelete);
		List<Shift> created = shiftRepository.saveAll(toCreate);
		List<Long> emptyShiftIds = new ArrayList<>();
		retained.stream().filter(shift -> shift.getWorker() == null).map(Shift::getId).forEach(emptyShiftIds::add);
		created.stream().filter(shift -> shift.getWorker() == null).map(Shift::getId).forEach(emptyShiftIds::add);
		return new BulkSaveResult(created.size(), toDelete.size(), emptyShiftIds);
	}

	@Transactional
	public ManagedShift createShift(
			Long actorId,
			Long storeId,
			Long workerId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String position
	) {
		Store store = findStore(storeId);
		requireStoreManagement(actorId, store);
		User actor = userRepository.findById(actorId).orElseThrow(InvalidCredentialsException::new);
		validateShift(startAt, endAt, position);
		User worker = validateAndFindWorker(store, workerId, startAt, endAt, null);
		Shift shift = shiftRepository.save(Shift.create(
				store,
				worker,
				startAt,
				endAt,
				normalizePosition(position),
				actor
		));
		return toManagedShift(shift);
	}

	@Transactional
	public ManagedShift updateShift(
			Long actorId,
			Long shiftId,
			Long workerId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String position
	) {
		Shift shift = shiftRepository.findById(shiftId).orElseThrow(ShiftNotFoundException::new);
		requireStoreManagement(actorId, shift.getStore());
		validateShift(startAt, endAt, position);
		User worker = validateAndFindWorker(shift.getStore(), workerId, startAt, endAt, shiftId);
		shift.update(worker, startAt, endAt, normalizePosition(position));
		return toManagedShift(shift);
	}

	@Transactional
	public void deleteShift(Long actorId, Long shiftId) {
		Shift shift = shiftRepository.findById(shiftId).orElseThrow(ShiftNotFoundException::new);
		requireStoreManagement(actorId, shift.getStore());
		shiftRepository.delete(shift);
	}

	private Store findStore(Long storeId) {
		return storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
	}

	private void requireStoreAccess(Long actorId, Store store) {
		if (store.getOwner().getId().equals(actorId)) {
			return;
		}
		if (!storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
				store.getId(), actorId, MemberStatus.ACTIVE)) {
			throw new StoreManagementForbiddenException();
		}
	}

	private void requireStoreManagement(Long actorId, Store store) {
		if (store.getOwner().getId().equals(actorId)) {
			return;
		}
		boolean activeManager = storeMemberRepository.findByStore_IdAndUser_Id(store.getId(), actorId)
				.filter(membership -> membership.getStatus() == MemberStatus.ACTIVE)
				.filter(membership -> membership.getRole() == MemberRole.MANAGER)
				.isPresent();
		if (!activeManager) {
			throw new StoreManagementForbiddenException();
		}
	}

	private User validateAndFindWorker(
			Store store,
			Long workerId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			Long excludeShiftId
	) {
		if (workerId == null) {
			return null;
		}
		User worker = findEligibleWorker(store, workerId);
		long ignoredShiftId = excludeShiftId == null ? -1L : excludeShiftId;
		if (shiftRepository.existsOverlappingShift(workerId, startAt, endAt, ignoredShiftId)) {
			throw new ShiftTimeConflictException();
		}
		return worker;
	}

	private User findEligibleWorker(Store store, Long workerId) {
		if (storeRepository.existsByOwner_Id(workerId)) {
			throw new InvalidShiftWorkerException();
		}
		boolean eligible = storeMemberRepository.existsByUser_IdAndStatusAndStore_Owner_Id(
				workerId,
				MemberStatus.ACTIVE,
				store.getOwner().getId()
		);
		if (!eligible) {
			throw new InvalidShiftWorkerException();
		}
		return userRepository.findById(workerId).orElseThrow(InvalidShiftWorkerException::new);
	}

	private List<ShiftBulkConflictException.Conflict> findBulkConflicts(
			Store store,
			List<PreparedShift> prepared,
			Set<Long> replaceableIds
	) {
		List<ShiftBulkConflictException.Conflict> conflicts = new ArrayList<>();
		Map<Long, List<PreparedShift>> byWorker = new HashMap<>();
		for (PreparedShift item : prepared) {
			if (item.worker() != null) {
				byWorker.computeIfAbsent(item.worker().getId(), ignored -> new ArrayList<>()).add(item);
			}
		}

		for (List<PreparedShift> workerShifts : byWorker.values()) {
			workerShifts.sort(Comparator.comparing(PreparedShift::startAt));
			for (int index = 1; index < workerShifts.size(); index++) {
				PreparedShift previous = workerShifts.get(index - 1);
				PreparedShift current = workerShifts.get(index);
				if (current.startAt().isBefore(previous.endAt())) {
					conflicts.add(toConflict(current, store.getName(), current.startAt(), current.endAt()));
				}
			}
		}

		for (PreparedShift item : prepared) {
			if (item.worker() == null) {
				continue;
			}
			for (Shift overlap : shiftRepository.findOverlappingShifts(
					item.worker().getId(), item.startAt(), item.endAt())) {
				if (!replaceableIds.contains(overlap.getId())) {
					conflicts.add(toConflict(
							item,
							overlap.getStore().getName(),
							overlap.getStartAt(),
							overlap.getEndAt()
					));
				}
			}
		}
		return conflicts;
	}

	private ShiftBulkConflictException.Conflict toConflict(
			PreparedShift item,
			String storeName,
			LocalDateTime startAt,
			LocalDateTime endAt
	) {
		return new ShiftBulkConflictException.Conflict(
				item.worker().getId(),
				item.worker().getName(),
				item.startAt().toLocalDate(),
				storeName,
				startAt,
				endAt
		);
	}

	private void validateShift(LocalDateTime startAt, LocalDateTime endAt, String position) {
		if (!endAt.isAfter(startAt)) {
			throw new InvalidShiftException("근무 종료 시간은 시작 시간보다 늦어야 해요.");
		}
		if (StringUtils.hasText(position) && position.trim().length() > 10) {
			throw new InvalidShiftException("시간대 이름은 10자 이하로 입력해 주세요.");
		}
	}

	private void validateTemplates(List<TemplateCommand> commands) {
		Set<String> names = new HashSet<>();
		for (TemplateCommand command : commands) {
			String name = command.name().trim();
			if (name.isEmpty() || name.length() > 10 || name.contains("|")) {
				throw new InvalidShiftException("시간대 이름은 | 없이 1~10자로 입력해 주세요.");
			}
			if (!names.add(name)) {
				throw new InvalidShiftException("같은 시간대 이름을 두 번 사용할 수 없어요.");
			}
			if (!command.endTime().isAfter(command.startTime())) {
				throw new InvalidShiftException("시간대 종료 시간은 시작 시간보다 늦어야 해요.");
			}
			if (command.requiredCount() < 1 || command.requiredCount() > 9) {
				throw new InvalidShiftException("필요 인원은 1~9명으로 입력해 주세요.");
			}
		}
	}

	private ManagedShift toManagedShift(Shift shift) {
		boolean helper = shift.getWorker() != null
				&& !storeMemberRepository.existsByStore_IdAndUser_IdAndStatus(
						shift.getStore().getId(),
						shift.getWorker().getId(),
						MemberStatus.ACTIVE
				);
		return new ManagedShift(shift, helper);
	}

	private String normalizePosition(String position) {
		return StringUtils.hasText(position) ? position.trim() : null;
	}

	private ShiftProgress progressOf(Shift shift, LocalDateTime now) {
		if (now.isBefore(shift.getStartAt())) {
			return ShiftProgress.UPCOMING;
		}
		if (now.isBefore(shift.getEndAt())) {
			return ShiftProgress.IN_PROGRESS;
		}
		return ShiftProgress.DONE;
	}

	public record TemplateCommand(
			String name,
			LocalTime startTime,
			LocalTime endTime,
			int requiredCount
	) {
	}

	public record ManagedShift(Shift shift, boolean helper) {
	}

	public record MyShift(Shift shift, boolean helper, ShiftProgress status) {
	}

	public record BulkItemCommand(Long workerId, LocalDate date, String position) {
	}

	public record BulkSaveResult(int created, int deleted, List<Long> emptyShiftIds) {
	}

	private record PreparedShift(
			User worker,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String position
	) {
	}

	private record ShiftKey(
			Long workerId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String position
	) {
		private static ShiftKey from(Shift shift) {
			return new ShiftKey(
					shift.getWorker() == null ? null : shift.getWorker().getId(),
					shift.getStartAt(),
					shift.getEndAt(),
					shift.getPosition()
			);
		}

		private static ShiftKey from(PreparedShift shift) {
			return new ShiftKey(
					shift.worker() == null ? null : shift.worker().getId(),
					shift.startAt(),
					shift.endAt(),
					shift.position()
			);
		}
	}
}
