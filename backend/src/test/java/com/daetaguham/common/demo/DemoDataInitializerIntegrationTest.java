package com.daetaguham.common.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.daetaguham.notification.domain.NotificationRepository;
import com.daetaguham.request.domain.RequestApplicationRepository;
import com.daetaguham.request.domain.RequestAvailableDateRepository;
import com.daetaguham.request.domain.ShiftRequestRepository;
import com.daetaguham.shift.domain.ShiftRepository;
import com.daetaguham.shift.domain.ShiftTemplateRepository;
import com.daetaguham.store.domain.MemberRole;
import com.daetaguham.store.domain.MemberStatus;
import com.daetaguham.store.domain.StoreMemberRepository;
import com.daetaguham.store.domain.StoreNoticeRepository;
import com.daetaguham.store.domain.StoreRepository;
import com.daetaguham.user.domain.AvailabilityRepository;
import com.daetaguham.user.domain.User;
import com.daetaguham.user.domain.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:demo-seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"app.demo.seed-enabled=true",
		"app.demo.password=demo-test-password"
})
@Transactional
class DemoDataInitializerIntegrationTest {

	@Autowired private DemoDataInitializer initializer;
	@Autowired private UserRepository userRepository;
	@Autowired private StoreRepository storeRepository;
	@Autowired private StoreMemberRepository storeMemberRepository;
	@Autowired private ShiftTemplateRepository shiftTemplateRepository;
	@Autowired private ShiftRepository shiftRepository;
	@Autowired private AvailabilityRepository availabilityRepository;
	@Autowired private StoreNoticeRepository storeNoticeRepository;
	@Autowired private ShiftRequestRepository shiftRequestRepository;
	@Autowired private RequestAvailableDateRepository requestAvailableDateRepository;
	@Autowired private RequestApplicationRepository requestApplicationRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	@Test
	void createsCompleteDemoScenarioOnce() {
		assertThat(userRepository.count()).isEqualTo(5);
		assertThat(storeRepository.count()).isEqualTo(1);
		assertThat(storeMemberRepository.count()).isEqualTo(4);
		assertThat(shiftTemplateRepository.count()).isEqualTo(3);
		assertThat(shiftRepository.count()).isEqualTo(64);
		assertThat(availabilityRepository.count()).isEqualTo(12);
		assertThat(storeNoticeRepository.count()).isEqualTo(2);
		assertThat(shiftRequestRepository.count()).isEqualTo(3);
		assertThat(requestAvailableDateRepository.count()).isEqualTo(3);
		assertThat(requestApplicationRepository.count()).isEqualTo(2);
		assertThat(notificationRepository.count()).isEqualTo(4);

		User owner = userRepository.findByPhone("010-9000-0001").orElseThrow();
		assertThat(owner.getName()).isEqualTo("김효주");
		assertThat(passwordEncoder.matches("demo-test-password", owner.getPasswordHash())).isTrue();

		User manager = userRepository.findByPhone("010-9000-0002").orElseThrow();
		Long storeId = storeRepository.findAllByOwner_Id(owner.getId()).getFirst().getId();
		assertThat(storeMemberRepository.findByStore_IdAndUser_Id(storeId, manager.getId()))
				.get()
				.satisfies(member -> {
					assertThat(member.getRole()).isEqualTo(MemberRole.MANAGER);
					assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
				});

		initializer.run(new DefaultApplicationArguments(new String[0]));
		assertThat(userRepository.count()).isEqualTo(5);
		assertThat(shiftRepository.count()).isEqualTo(64);
		assertThat(shiftRequestRepository.count()).isEqualTo(3);
	}
}
