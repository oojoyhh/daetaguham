package com.daetaguham.user.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

	List<Availability> findAllByUser_IdOrderByDayOfWeekAsc(Long userId);

	void deleteAllByUser_Id(Long userId);
}
