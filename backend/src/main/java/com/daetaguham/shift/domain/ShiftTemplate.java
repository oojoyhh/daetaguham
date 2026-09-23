package com.daetaguham.shift.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

import com.daetaguham.store.domain.Store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
		name = "shift_templates",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_shift_templates_store_name",
				columnNames = {"store_id", "name"}
		)
)
public class ShiftTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;

	@Column(nullable = false, length = 10)
	private String name;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	@Column(nullable = false)
	private int requiredCount;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ShiftTemplate() {
	}

	private ShiftTemplate(Store store, String name, LocalTime startTime, LocalTime endTime, int requiredCount) {
		this.store = Objects.requireNonNull(store);
		update(name, startTime, endTime, requiredCount);
	}

	public static ShiftTemplate create(
			Store store,
			String name,
			LocalTime startTime,
			LocalTime endTime,
			int requiredCount
	) {
		return new ShiftTemplate(store, name, startTime, endTime, requiredCount);
	}

	public void update(String name, LocalTime startTime, LocalTime endTime, int requiredCount) {
		this.name = Objects.requireNonNull(name);
		this.startTime = Objects.requireNonNull(startTime);
		this.endTime = Objects.requireNonNull(endTime);
		this.requiredCount = requiredCount;
	}

	@PrePersist
	void assignCreatedAt() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Store getStore() {
		return store;
	}

	public String getName() {
		return name;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public int getRequiredCount() {
		return requiredCount;
	}
}
