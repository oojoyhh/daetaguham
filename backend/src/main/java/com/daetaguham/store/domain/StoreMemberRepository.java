package com.daetaguham.store.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {

	Optional<StoreMember> findByStore_IdAndUser_Id(Long storeId, Long userId);

	List<StoreMember> findAllByUser_IdAndStatus(Long userId, MemberStatus status);

	boolean existsByStore_IdAndUser_Id(Long storeId, Long userId);
}
