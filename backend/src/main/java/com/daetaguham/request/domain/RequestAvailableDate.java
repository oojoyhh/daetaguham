package com.daetaguham.request.domain;

import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "request_available_dates",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_request_available_dates_request_date",
				columnNames = {"request_id", "available_date"}
		)
)
public class RequestAvailableDate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private ShiftRequest request;

	@Column(nullable = false)
	private LocalDate availableDate;

	protected RequestAvailableDate() {
	}

	private RequestAvailableDate(ShiftRequest request, LocalDate availableDate) {
		this.request = Objects.requireNonNull(request);
		this.availableDate = Objects.requireNonNull(availableDate);
	}

	public static RequestAvailableDate create(ShiftRequest request, LocalDate availableDate) {
		return new RequestAvailableDate(request, availableDate);
	}

	public Long getId() {
		return id;
	}

	public ShiftRequest getRequest() {
		return request;
	}

	public LocalDate getAvailableDate() {
		return availableDate;
	}
}
