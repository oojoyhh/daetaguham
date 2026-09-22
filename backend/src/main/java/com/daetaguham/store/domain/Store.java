package com.daetaguham.store.domain;

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
@Table(name = "stores")
public class Store {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 30)
	private String category;

	@Column(length = 200)
	private String address;

	@Column(nullable = false, unique = true, length = 6)
	private String inviteCode;

	@Column(nullable = false)
	private boolean approvalRequired;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Store() {
	}

	private Store(User owner, String name, String category, String address, String inviteCode) {
		this.owner = Objects.requireNonNull(owner);
		this.name = Objects.requireNonNull(name);
		this.category = Objects.requireNonNull(category);
		this.address = address;
		this.inviteCode = Objects.requireNonNull(inviteCode);
		this.approvalRequired = true;
	}

	public static Store create(User owner, String name, String category, String address, String inviteCode) {
		return new Store(owner, name, category, address, inviteCode);
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

	public User getOwner() {
		return owner;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public String getAddress() {
		return address;
	}

	public String getInviteCode() {
		return inviteCode;
	}

	public boolean isApprovalRequired() {
		return approvalRequired;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
