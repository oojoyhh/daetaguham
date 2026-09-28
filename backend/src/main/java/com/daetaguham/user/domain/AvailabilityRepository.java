package com.daetaguham.user.domain;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

	List<Availability> findAllByUser_IdOrderByDayOfWeekAsc(Long userId);

	@EntityGraph(attributePaths = "user")
	List<Availability> findAllByUser_IdInAndDayOfWeek(
			Collection<Long> userIds, short dayOfWeek);

	void deleteAllByUser_Id(Long userId);
}
