package com.daetaguham.shift.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

	@EntityGraph(attributePaths = {"store", "worker"})
	List<Shift> findAllByStore_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
			Long storeId,
			LocalDateTime from,
			LocalDateTime toExclusive
	);

	@EntityGraph(attributePaths = {"store", "worker"})
	List<Shift> findAllByWorker_IdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
			Long workerId,
			LocalDateTime from,
			LocalDateTime toExclusive
	);

	@Query("""
			select (count(shift) > 0)
			from Shift shift
			where shift.worker.id = :workerId
			  and shift.startAt < :endAt
			  and shift.endAt > :startAt
			  and shift.id <> :excludeId
			""")
	boolean existsOverlappingShift(
			@Param("workerId") Long workerId,
			@Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt,
			@Param("excludeId") Long excludeId
	);

	@EntityGraph(attributePaths = {"store", "worker"})
	@Query("""
			select shift
			from Shift shift
			where shift.worker.id = :workerId
			  and shift.startAt < :endAt
			  and shift.endAt > :startAt
			""")
	List<Shift> findOverlappingShifts(
			@Param("workerId") Long workerId,
			@Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt
	);
}
