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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "request_applications",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_request_applications_request_applicant",
				columnNames = {"request_id", "applicant_id"}
		)
)
public class RequestApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private ShiftRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "applicant_id", nullable = false)
	private User applicant;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "selected_offer_shift_id")
	private Shift selectedOfferShift;

	@Column(length = 60)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ApplicationStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private User decidedBy;

	@Column(length = 200)
	private String rejectComment;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime respondedAt;

	protected RequestApplication() {
	}

	private RequestApplication(
			ShiftRequest request,
			User applicant,
			String message,
			ApplicationStatus status
	) {
		this.request = Objects.requireNonNull(request);
		this.applicant = Objects.requireNonNull(applicant);
		this.message = message;
		this.status = Objects.requireNonNull(status);
	}

	public static RequestApplication proposed(ShiftRequest request, User applicant) {
		return new RequestApplication(request, applicant, null, ApplicationStatus.PROPOSED);
	}

	public static RequestApplication applied(ShiftRequest request, User applicant, String message) {
		return new RequestApplication(request, applicant, message, ApplicationStatus.APPLIED);
	}

	public void reapply(String message) {
		if (status != ApplicationStatus.WITHDRAWN) {
			throw new IllegalStateException("철회한 지원만 다시 지원할 수 있어요.");
		}
		this.message = message;
		this.status = ApplicationStatus.APPLIED;
		this.selectedOfferShift = null;
		this.respondedAt = null;
	}

	public void withdraw() {
		if (status != ApplicationStatus.APPLIED) {
			throw new IllegalStateException("선택 전 지원만 철회할 수 있어요.");
		}
		status = ApplicationStatus.WITHDRAWN;
		respondedAt = LocalDateTime.now();
	}

	public void decline() {
		if (status != ApplicationStatus.PROPOSED) {
			throw new IllegalStateException("응답 대기 중인 제안만 거절할 수 있어요.");
		}
		status = ApplicationStatus.DECLINED;
		respondedAt = LocalDateTime.now();
	}

	public void select(Shift selectedOfferShift) {
		if (status != ApplicationStatus.PROPOSED && status != ApplicationStatus.APPLIED) {
			throw new IllegalStateException("대기 중인 제안이나 지원만 선택할 수 있어요.");
		}
		status = ApplicationStatus.SELECTED;
		this.selectedOfferShift = selectedOfferShift;
		respondedAt = LocalDateTime.now();
	}

	public void markNotSelected() {
		if (status == ApplicationStatus.APPLIED
				|| status == ApplicationStatus.PROPOSED
				|| status == ApplicationStatus.SELECTED) {
			status = ApplicationStatus.NOT_SELECTED;
			respondedAt = LocalDateTime.now();
		}
	}

	public void reject(User decider, String comment) {
		if (status != ApplicationStatus.SELECTED) {
			throw new IllegalStateException("선택된 지원만 반려할 수 있어요.");
		}
		status = ApplicationStatus.REJECTED;
		decidedBy = Objects.requireNonNull(decider);
		rejectComment = Objects.requireNonNull(comment);
		respondedAt = LocalDateTime.now();
	}

	public void approveBy(User decider) {
		if (status != ApplicationStatus.SELECTED) {
			throw new IllegalStateException("선택된 지원만 승인할 수 있어요.");
		}
		decidedBy = Objects.requireNonNull(decider);
	}

	@PrePersist
	void assignCreatedAt() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Long getId() { return id; }
	public ShiftRequest getRequest() { return request; }
	public User getApplicant() { return applicant; }
	public Shift getSelectedOfferShift() { return selectedOfferShift; }
	public String getMessage() { return message; }
	public ApplicationStatus getStatus() { return status; }
	public User getDecidedBy() { return decidedBy; }
	public String getRejectComment() { return rejectComment; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getRespondedAt() { return respondedAt; }
}
