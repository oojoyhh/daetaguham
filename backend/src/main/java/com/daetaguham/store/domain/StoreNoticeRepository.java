package com.daetaguham.store.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreNoticeRepository extends JpaRepository<StoreNotice, Long> {

	@EntityGraph(attributePaths = {"store", "createdBy"})
	List<StoreNotice> findAllByStore_IdAndNoticeDateGreaterThanEqualOrderByNoticeDateAscCreatedAtAsc(
			Long storeId,
			LocalDate from
	);

	@EntityGraph(attributePaths = {"store", "createdBy"})
	List<StoreNotice> findAllByStore_IdInAndNoticeDateGreaterThanEqualOrderByNoticeDateAscCreatedAtAsc(
			Collection<Long> storeIds,
			LocalDate from
	);

	@EntityGraph(attributePaths = {"store", "createdBy"})
	Optional<StoreNotice> findDetailedById(Long id);
}
