package com.daetaguham.store.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class StoreMembershipRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StoreRepository storeRepository;

	@Autowired
	private StoreMemberRepository storeMemberRepository;

	@Test
	void savesOwnerStoreAndPendingWorkerMembership() {
		User owner = userRepository.save(User.create("010-1111-2222", "owner-password-hash", "김효주"));
		User worker = userRepository.save(User.create("010-2222-3333", "worker-password-hash", "김민"));
		Store store = storeRepository.save(Store.create(
				owner,
				"성수점",
				"아이스크림·디저트",
				"서울 성동구",
				"SEONG7"
		));

		storeMemberRepository.save(StoreMember.request(store, worker));

		Optional<StoreMember> saved = storeMemberRepository.findByStore_IdAndUser_Id(
				store.getId(),
				worker.getId()
		);

		assertTrue(saved.isPresent());
		assertEquals(MemberRole.WORKER, saved.orElseThrow().getRole());
		assertEquals(MemberStatus.PENDING, saved.orElseThrow().getStatus());
		assertEquals(owner.getId(), storeRepository.findByInviteCode("SEONG7").orElseThrow().getOwner().getId());
	}

	@Test
	void findsUserByPhoneAndOwnedStoresByOwner() {
		User owner = userRepository.save(User.create("010-4444-5555", "owner-password-hash", "김효주"));
		storeRepository.save(Store.create(owner, "성수점", "아이스크림·디저트", null, "ABC123"));

		assertTrue(userRepository.findByPhone("010-4444-5555").isPresent());
		assertEquals(1, storeRepository.findAllByOwner_Id(owner.getId()).size());
	}
}
