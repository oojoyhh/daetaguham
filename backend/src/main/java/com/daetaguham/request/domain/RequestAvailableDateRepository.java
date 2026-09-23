package com.daetaguham.request.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestAvailableDateRepository extends JpaRepository<RequestAvailableDate, Long> {

	List<RequestAvailableDate> findAllByRequest_IdOrderByAvailableDateAsc(Long requestId);
}
