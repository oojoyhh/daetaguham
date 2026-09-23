package com.daetaguham.request.domain;

import java.util.Collection;
import java.util.Optional;

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
}
