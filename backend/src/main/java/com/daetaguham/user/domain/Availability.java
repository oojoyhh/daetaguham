package com.daetaguham.user.domain;

import java.time.LocalTime;
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
		name = "availabilities",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_availabilities_user_day",
				columnNames = {"user_id", "day_of_week"}
		)
)
public class Availability {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private short dayOfWeek;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	protected Availability() {
	}

	private Availability(User user, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
		this.user = Objects.requireNonNull(user);
		this.dayOfWeek = (short) dayOfWeek;
		this.startTime = Objects.requireNonNull(startTime);
		this.endTime = Objects.requireNonNull(endTime);
	}

	public static Availability create(User user, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
		return new Availability(user, dayOfWeek, startTime, endTime);
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public int getDayOfWeek() {
		return dayOfWeek;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}
}
