package com.daetaguham.store.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import com.daetaguham.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "store_members",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_store_members_store_user",
				columnNames = {"store_id", "user_id"}
		)
)
public class StoreMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime requestedAt;

	private LocalDateTime joinedAt;

	private LocalDateTime leftAt;

	protected StoreMember() {
	}

	private StoreMember(Store store, User user) {
		this.store = Objects.requireNonNull(store);
		this.user = Objects.requireNonNull(user);
		this.role = MemberRole.WORKER;
		this.status = MemberStatus.PENDING;
	}

	public static StoreMember request(Store store, User user) {
		return new StoreMember(store, user);
	}

	@PrePersist
	void assignRequestedAt() {
		if (requestedAt == null) {
			requestedAt = LocalDateTime.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Store getStore() {
		return store;
	}

	public User getUser() {
		return user;
	}

	public MemberRole getRole() {
		return role;
	}

	public MemberStatus getStatus() {
		return status;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getJoinedAt() {
		return joinedAt;
	}

	public LocalDateTime getLeftAt() {
		return leftAt;
	}
}
