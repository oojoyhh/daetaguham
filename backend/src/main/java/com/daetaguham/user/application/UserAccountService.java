package com.daetaguham.user.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.daetaguham.common.security.JwtTokenService;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final String dummyPasswordHash;

	public UserAccountService(
			UserRepository userRepository,
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService
	) {
		this.userRepository = userRepository;
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-only");
	}

	@Transactional(readOnly = true)
	public LoginResult login(String phone, String rawPassword) {
		User user = userRepository.findByPhone(phone).orElse(null);
		String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
		boolean passwordMatches = passwordEncoder.matches(rawPassword, passwordHash);
		if (user == null || !passwordMatches) {
			throw new InvalidCredentialsException();
		}

		AccountResult account = accountOf(user);
		return new LoginResult(jwtTokenService.issue(user), account);
	}

	@Transactional(readOnly = true)
	public AccountResult getAccount(Long userId) {
		User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
		return accountOf(user);
	}

	private AccountResult accountOf(User user) {
		List<UserStoreView> stores = new ArrayList<>();
		for (Store store : storeRepository.findAllByOwner_Id(user.getId())) {
			stores.add(new UserStoreView(store.getId(), store.getName(), "OWNER", null, null));
		}
		for (StoreMember membership : storeMemberRepository.findAllByUser_IdAndStatusIn(
				user.getId(),
				EnumSet.of(MemberStatus.PENDING, MemberStatus.ACTIVE)
		)) {
			stores.add(new UserStoreView(
					membership.getStore().getId(),
					membership.getStore().getName(),
					membership.getRole().name(),
					membership.getStatus(),
					membership.getJoinedAt()
			));
		}
		stores.sort(Comparator
				.comparingInt((UserStoreView store) -> roleOrder(store.myRole()))
				.thenComparing(UserStoreView::storeName));
		return new AccountResult(user, List.copyOf(stores));
	}

	private int roleOrder(String role) {
		return switch (role) {
			case "OWNER" -> 0;
			case "MANAGER" -> 1;
			default -> 2;
		};
	}

	public record LoginResult(String token, AccountResult account) {
	}

	public record AccountResult(User user, List<UserStoreView> stores) {
	}

	public record UserStoreView(
			Long storeId,
			String storeName,
			String myRole,
			MemberStatus memberStatus,
			LocalDateTime joinedAt
	) {
	}
}
