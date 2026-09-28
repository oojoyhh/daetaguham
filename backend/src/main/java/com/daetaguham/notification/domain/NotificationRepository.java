package com.daetaguham.notification.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@EntityGraph(attributePaths = {"request", "store"})
	Page<Notification> findAllByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	@EntityGraph(attributePaths = {"request", "store"})
	Page<Notification> findAllByUser_IdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

	long countByUser_IdAndIsReadFalse(Long userId);

	Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

	@Modifying(clearAutomatically = true)
	@Query("update Notification notification set notification.isRead = true "
			+ "where notification.user.id = :userId and notification.isRead = false")
	int markAllReadByUserId(@Param("userId") Long userId);
}
