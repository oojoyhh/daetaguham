package com.daetaguham.request.domain;

import java.util.Objects;

import com.daetaguham.shift.domain.Shift;

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
		name = "application_offer_shifts",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_application_offer_shifts_application_shift",
				columnNames = {"application_id", "shift_id"}
		)
)
public class ApplicationOfferShift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false)
	private RequestApplication application;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shift_id", nullable = false)
	private Shift shift;

	protected ApplicationOfferShift() {
	}

	private ApplicationOfferShift(RequestApplication application, Shift shift) {
		this.application = Objects.requireNonNull(application);
		this.shift = Objects.requireNonNull(shift);
	}

	public static ApplicationOfferShift create(RequestApplication application, Shift shift) {
		return new ApplicationOfferShift(application, shift);
	}

	public Long getId() { return id; }
	public RequestApplication getApplication() { return application; }
	public Shift getShift() { return shift; }
}
