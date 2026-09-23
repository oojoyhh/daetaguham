package com.daetaguham.store.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.daetaguham.shift.application.ShiftService;
import com.daetaguham.shift.application.ShiftService.ManagedShift;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreOverviewService {

	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final ShiftService shiftService;

	public StoreOverviewService(
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			ShiftService shiftService
	) {
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.shiftService = shiftService;
	}

	@Transactional(readOnly = true)
	public StoreOverview today(Long actorId, Long storeId) {
		Store store = storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
		requireStoreManagement(actorId, store);
		LocalDate today = LocalDate.now();
		LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate weekEnd = weekStart.plusDays(6);
		List<ManagedShift> todayShifts = shiftService.findShifts(actorId, storeId, today, today);
		List<ManagedShift> emptyShifts = shiftService.findShifts(actorId, storeId, weekStart, weekEnd).stream()
				.filter(shift -> shift.shift().getWorker() == null)
				.toList();
		int todayWorkerCount = (int) todayShifts.stream()
				.map(shift -> shift.shift().getWorker())
				.filter(java.util.Objects::nonNull)
				.map(worker -> worker.getId())
				.distinct()
				.count();
		int pendingMemberCount = Math.toIntExact(
				storeMemberRepository.countByStore_IdAndStatus(storeId, MemberStatus.PENDING));
		return new StoreOverview(
				todayWorkerCount,
				0,
				pendingMemberCount,
				emptyShifts.size(),
				0,
				todayShifts,
				emptyShifts
		);
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

	public record StoreOverview(
			int todayWorkerCount,
			int pendingApprovalCount,
			int pendingMemberCount,
			int emptyShiftCount,
			int openShiftCount,
			List<ManagedShift> todayShifts,
			List<ManagedShift> emptyShifts
	) {
	}
}
