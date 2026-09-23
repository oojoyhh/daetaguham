package com.daetaguham.store.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {

	@EntityGraph(attributePaths = {"store", "user"})
	Optional<StoreMember> findByStore_IdAndUser_Id(Long storeId, Long userId);

	@EntityGraph(attributePaths = "user")
	List<StoreMember> findAllByStore_IdOrderByRequestedAtAsc(Long storeId);

	@EntityGraph(attributePaths = "user")
	List<StoreMember> findAllByStore_IdAndStatusOrderByRequestedAtAsc(Long storeId, MemberStatus status);

	List<StoreMember> findAllByUser_IdAndStatus(Long userId, MemberStatus status);

	@EntityGraph(attributePaths = "store")
	List<StoreMember> findAllByUser_IdAndStatusIn(Long userId, Collection<MemberStatus> statuses);

	boolean existsByStore_IdAndUser_Id(Long storeId, Long userId);
}
