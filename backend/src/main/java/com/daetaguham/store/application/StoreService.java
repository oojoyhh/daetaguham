package com.daetaguham.store.application;

import java.time.LocalTime;
import java.util.List;

import com.daetaguham.shift.domain.ShiftTemplate;
import com.daetaguham.shift.domain.ShiftTemplateRepository;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.Store;
import com.daetaguham.store.domain.StoreMember;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.application.InvalidCredentialsException;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StoreService {

	private static final int INVITE_CODE_ATTEMPTS = 20;

	private final UserRepository userRepository;
	private final StoreRepository storeRepository;
	private final StoreMemberRepository storeMemberRepository;
	private final ShiftTemplateRepository shiftTemplateRepository;
	private final InviteCodeGenerator inviteCodeGenerator;

	public StoreService(
			UserRepository userRepository,
			StoreRepository storeRepository,
			StoreMemberRepository storeMemberRepository,
			ShiftTemplateRepository shiftTemplateRepository,
			InviteCodeGenerator inviteCodeGenerator
	) {
		this.userRepository = userRepository;
		this.storeRepository = storeRepository;
		this.storeMemberRepository = storeMemberRepository;
		this.shiftTemplateRepository = shiftTemplateRepository;
		this.inviteCodeGenerator = inviteCodeGenerator;
	}

	@Transactional
	public Store create(Long ownerId, String name, String category, String address) {
		User owner = userRepository.findById(ownerId).orElseThrow(InvalidCredentialsException::new);
		Store store = Store.create(
				owner,
				name.trim(),
				category.trim(),
				normalizeAddress(address),
				createUniqueInviteCode()
		);
		Store savedStore = storeRepository.save(store);
		shiftTemplateRepository.saveAll(List.of(
				ShiftTemplate.create(savedStore, "오픈", LocalTime.of(10, 0), LocalTime.of(16, 0), 1),
				ShiftTemplate.create(savedStore, "미들", LocalTime.of(12, 0), LocalTime.of(18, 0), 1),
				ShiftTemplate.create(savedStore, "마감", LocalTime.of(16, 0), LocalTime.of(22, 0), 1)
		));
		return savedStore;
	}

	@Transactional
	public StoreMember join(Long userId, String inviteCode) {
		User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
		Store store = storeRepository.findByInviteCode(inviteCode)
				.orElseThrow(() -> new StoreNotFoundException("해당 초대코드의 매장을 찾을 수 없어요."));
		if (store.getOwner().getId().equals(userId)) {
			throw new OwnerCannotJoinException();
		}

		StoreMember membership = storeMemberRepository.findByStore_IdAndUser_Id(store.getId(), userId)
				.orElse(null);
		if (membership == null) {
			return storeMemberRepository.save(StoreMember.request(store, user));
		}
		if (membership.getStatus() == MemberStatus.PENDING || membership.getStatus() == MemberStatus.ACTIVE) {
			throw new ExistingMembershipException();
		}

		membership.reapply();
		return membership;
	}

	private String createUniqueInviteCode() {
		for (int attempt = 0; attempt < INVITE_CODE_ATTEMPTS; attempt++) {
			String inviteCode = inviteCodeGenerator.generate();
			if (!storeRepository.existsByInviteCode(inviteCode)) {
				return inviteCode;
			}
		}
		throw new IllegalStateException("초대코드를 발급하지 못했습니다.");
	}

	private String normalizeAddress(String address) {
		return StringUtils.hasText(address) ? address.trim() : null;
	}
}
