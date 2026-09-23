package com.daetaguham.shift.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
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
		User worker = userRepository.findById(workerId).orElseThrow(InvalidShiftWorkerException::new);
		long ignoredShiftId = excludeShiftId == null ? -1L : excludeShiftId;
		if (shiftRepository.existsOverlappingShift(workerId, startAt, endAt, ignoredShiftId)) {
			throw new ShiftTimeConflictException();
		}
		return worker;
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

	public record TemplateCommand(
			String name,
			LocalTime startTime,
			LocalTime endTime,
			int requiredCount
	) {
	}

	public record ManagedShift(Shift shift, boolean helper) {
	}
}
