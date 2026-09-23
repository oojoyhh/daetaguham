package com.daetaguham.store.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "store_notices")
public class StoreNotice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;

	@Column(nullable = false)
	private LocalDate noticeDate;

	@Column(nullable = false, length = 160)
	private String content;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected StoreNotice() {
	}

	private StoreNotice(Store store, LocalDate noticeDate, String content, User createdBy) {
		this.store = Objects.requireNonNull(store);
		this.noticeDate = Objects.requireNonNull(noticeDate);
		this.content = Objects.requireNonNull(content);
		this.createdBy = Objects.requireNonNull(createdBy);
	}

	public static StoreNotice create(Store store, LocalDate noticeDate, String content, User createdBy) {
		return new StoreNotice(store, noticeDate, content, createdBy);
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

	public LocalDate getNoticeDate() {
		return noticeDate;
	}

	public String getContent() {
		return content;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
