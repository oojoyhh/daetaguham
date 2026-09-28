package com.daetaguham.request.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRequestRepository extends JpaRepository<ShiftRequest, Long> {

	@EntityGraph(attributePaths = {"shift", "shift.store", "shift.worker", "requester"})
	@Query("select request from ShiftRequest request where request.id = :id")
	Optional<ShiftRequest> findDetailedById(@Param("id") Long id);

	boolean existsByShift_IdAndStatusIn(Long shiftId, Collection<RequestStatus> statuses);

	boolean existsByShift_Id(Long shiftId);

	long countByShift_Store_IdAndStatus(Long storeId, RequestStatus status);

	long countByShift_Store_IdAndTypeAndStatus(Long storeId, RequestType type, RequestStatus status);

	@EntityGraph(attributePaths = {"shift", "shift.store", "shift.worker", "requester"})
	List<ShiftRequest> findAllByShift_Store_IdAndStatusOrderByCreatedAtAsc(
			Long storeId, RequestStatus status);

	@EntityGraph(attributePaths = {"shift", "shift.store", "shift.worker", "requester"})
	List<ShiftRequest> findAllByShift_IdAndStatusIn(Long shiftId, Collection<RequestStatus> statuses);

	@EntityGraph(attributePaths = {"shift", "shift.store", "shift.store.owner", "shift.worker", "requester"})
	List<ShiftRequest> findAllByModeAndStatusOrderByCreatedAtDescIdDesc(
			RequestMode mode, RequestStatus status);

	@EntityGraph(attributePaths = {"shift", "shift.store", "shift.store.owner", "shift.worker", "requester"})
	Page<ShiftRequest> findAllByRequester_IdOrderByCreatedAtDescIdDesc(Long requesterId, Pageable pageable);
}
