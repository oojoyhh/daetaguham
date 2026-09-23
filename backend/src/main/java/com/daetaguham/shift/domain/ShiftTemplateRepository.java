package com.daetaguham.shift.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, Long> {

	List<ShiftTemplate> findAllByStore_IdOrderByStartTimeAsc(Long storeId);
}
