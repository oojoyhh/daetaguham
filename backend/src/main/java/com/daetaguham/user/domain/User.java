package com.daetaguham.user.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 13)
	private String phone;

	@Column(name = "password", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected User() {
	}

	private User(String phone, String passwordHash, String name) {
		this.phone = Objects.requireNonNull(phone);
		this.passwordHash = Objects.requireNonNull(passwordHash);
		this.name = Objects.requireNonNull(name);
	}

	public static User create(String phone, String passwordHash, String name) {
		return new User(phone, passwordHash, name);
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

	public String getPhone() {
		return phone;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getName() {
		return name;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
