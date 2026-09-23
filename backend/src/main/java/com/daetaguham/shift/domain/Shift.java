package com.daetaguham.shift.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import com.daetaguham.store.domain.Store;
import com.daetaguham.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "shifts")
public class Shift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "worker_id")
	private User worker;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	@Column(length = 10)
	private String position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Shift() {
	}

	private Shift(
			Store store,
			User worker,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String position,
			User createdBy
	) {
		this.store = Objects.requireNonNull(store);
		this.createdBy = Objects.requireNonNull(createdBy);
		update(worker, startAt, endAt, position);
	}

	public static Shift create(
			Store store,
			User worker,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String position,
			User createdBy
	) {
		return new Shift(store, worker, startAt, endAt, position, createdBy);
	}

	public void update(User worker, LocalDateTime startAt, LocalDateTime endAt, String position) {
		this.worker = worker;
		this.startAt = Objects.requireNonNull(startAt);
		this.endAt = Objects.requireNonNull(endAt);
		this.position = position;
	}

	@PrePersist
	void assignCreatedAt() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	@PreUpdate
	void assignUpdatedAt() {
		updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Store getStore() {
		return store;
	}

	public User getWorker() {
		return worker;
	}

	public LocalDateTime getStartAt() {
		return startAt;
	}

	public LocalDateTime getEndAt() {
		return endAt;
	}

	public String getPosition() {
		return position;
	}

	public User getCreatedBy() {
		return createdBy;
	}
}
