package com.daetaguham.request.domain;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationOfferShiftRepository extends JpaRepository<ApplicationOfferShift, Long> {

	@EntityGraph(attributePaths = {"shift", "shift.store", "shift.worker"})
	List<ApplicationOfferShift> findAllByApplication_IdOrderByShift_StartAtAsc(Long applicationId);

	void deleteAllByApplication_Id(Long applicationId);

	boolean existsByShift_Id(Long shiftId);
}
