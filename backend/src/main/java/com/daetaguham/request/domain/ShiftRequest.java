package com.daetaguham.request.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import com.daetaguham.shift.domain.Shift;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "shift_requests")
public class ShiftRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestMode mode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestScope scope;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shift_id", nullable = false)
	private Shift shift;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_id", nullable = false)
	private User requester;

	@Column(length = 100)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RequestStatus status;

	private LocalDateTime confirmedAt;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected ShiftRequest() {
	}

	private ShiftRequest(
			RequestType type,
			RequestMode mode,
			RequestScope scope,
			Shift shift,
			User requester,
			String reason
	) {
		this.type = Objects.requireNonNull(type);
		this.mode = Objects.requireNonNull(mode);
		this.scope = Objects.requireNonNull(scope);
		this.shift = Objects.requireNonNull(shift);
		this.requester = Objects.requireNonNull(requester);
		this.reason = reason;
		this.status = RequestStatus.OPEN;
	}

	public static ShiftRequest create(
			RequestType type,
			RequestMode mode,
			RequestScope scope,
			Shift shift,
			User requester,
			String reason
	) {
		return new ShiftRequest(type, mode, scope, shift, requester, reason);
	}

	public void cancel() {
		if (status != RequestStatus.OPEN && status != RequestStatus.PENDING_APPROVAL) {
			throw new IllegalStateException("진행 중인 요청만 취소할 수 있습니다.");
		}
		status = RequestStatus.CANCELED;
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

	public RequestType getType() {
		return type;
	}

	public RequestMode getMode() {
		return mode;
	}

	public RequestScope getScope() {
		return scope;
	}

	public Shift getShift() {
		return shift;
	}

	public User getRequester() {
		return requester;
	}

	public String getReason() {
		return reason;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
