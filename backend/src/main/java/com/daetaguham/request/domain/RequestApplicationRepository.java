package com.daetaguham.request.domain;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestApplicationRepository extends JpaRepository<RequestApplication, Long> {

	@EntityGraph(attributePaths = {"applicant", "request", "request.shift", "request.shift.store"})
	List<RequestApplication> findAllByRequest_IdOrderByCreatedAtAsc(Long requestId);

	@EntityGraph(attributePaths = {"applicant", "request", "request.shift", "request.shift.store"})
	Optional<RequestApplication> findByRequest_IdAndApplicant_Id(Long requestId, Long applicantId);

	boolean existsByRequest_IdAndApplicant_Id(Long requestId, Long applicantId);

	boolean existsByRequest_IdAndStatus(Long requestId, ApplicationStatus status);

	@EntityGraph(attributePaths = {"applicant", "request", "selectedOfferShift", "selectedOfferShift.store", "selectedOfferShift.worker"})
	Optional<RequestApplication> findByRequest_IdAndStatus(Long requestId, ApplicationStatus status);

	long countByRequest_IdAndStatusIn(Long requestId, Collection<ApplicationStatus> statuses);

	@EntityGraph(attributePaths = {"applicant", "request", "request.shift", "request.shift.store"})
	@Query("select application from RequestApplication application where application.id = :id")
	Optional<RequestApplication> findDetailedById(@Param("id") Long id);
}
