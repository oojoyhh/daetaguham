package com.daetaguham.store.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

	Optional<Store> findByInviteCode(String inviteCode);

	List<Store> findAllByOwner_Id(Long ownerId);

	boolean existsByOwner_Id(Long ownerId);

	boolean existsByInviteCode(String inviteCode);
}
