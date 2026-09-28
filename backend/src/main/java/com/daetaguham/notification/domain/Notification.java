package com.daetaguham.notification.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import com.daetaguham.request.domain.ShiftRequest;
import com.daetaguham.store.domain.Store;
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

@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id")
	private ShiftRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id")
	private Store store;

	@Column(nullable = false, length = 200)
	private String message;

	@Column(nullable = false)
	private boolean isRead;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Notification() {
	}

	private Notification(
			User user,
			NotificationType type,
			ShiftRequest request,
			Store store,
			String message
	) {
		this.user = Objects.requireNonNull(user);
		this.type = Objects.requireNonNull(type);
		this.request = request;
		this.store = store;
		this.message = Objects.requireNonNull(message);
		this.isRead = false;
	}

	public static Notification create(
			User user,
			NotificationType type,
			ShiftRequest request,
			Store store,
			String message
	) {
		return new Notification(user, type, request, store, message);
	}

	public void markRead() {
		isRead = true;
	}

	@PrePersist
	void assignCreatedAt() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Long getId() { return id; }
	public User getUser() { return user; }
	public NotificationType getType() { return type; }
	public ShiftRequest getRequest() { return request; }
	public Store getStore() { return store; }
	public String getMessage() { return message; }
	public boolean isRead() { return isRead; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
