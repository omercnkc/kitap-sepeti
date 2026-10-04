package com.kitapsepeti.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.kitapsepeti.common.outbox.OutboxEvent;
import com.kitapsepeti.common.outbox.OutboxRepository;
import com.kitapsepeti.user.TestcontainersConfiguration;
import com.kitapsepeti.user.entity.Address;
import com.kitapsepeti.user.entity.Role;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private OutboxRepository outboxRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesUserWithDefaultsAndFindsByEmail() {
		userRepository.saveAndFlush(newUser("ayse@kitapsepeti.com"));
		entityManager.clear();

		User found = userRepository.findByEmail("ayse@kitapsepeti.com").orElseThrow();

		assertThat(found.getId()).isNotNull();
		assertThat(found.getId().version()).isEqualTo(7);
		assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(found.getRole()).isEqualTo(Role.USER);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
		assertThat(userRepository.existsByEmail("ayse@kitapsepeti.com")).isTrue();
	}

	@Test
	void storesStatusInLowerCase() {
		userRepository.saveAndFlush(newUser("mehmet@kitapsepeti.com"));

		Object status = entityManager
			.createNativeQuery("SELECT status FROM users WHERE email = ?1")
			.setParameter(1, "mehmet@kitapsepeti.com")
			.getSingleResult();

		assertThat(status).isEqualTo("active");
	}

	@Test
	void rejectsSecondDefaultAddressForSameUser() {
		User user = userRepository.saveAndFlush(newUser("zeynep@kitapsepeti.com"));
		addressRepository.saveAndFlush(newDefaultAddress(user, "Line 1"));

		assertThatThrownBy(() -> addressRepository.saveAndFlush(newDefaultAddress(user, "Line 2")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void savesAndReadsOutboxJsonPayload() {
		UUID aggregateId = UUID.randomUUID();
		String payload = "{\"email\": \"ali@kitapsepeti.com\"}";
		OutboxEvent saved = outboxRepository.saveAndFlush(new OutboxEvent("User", aggregateId, "UserRegistered", payload));
		entityManager.clear();

		OutboxEvent found = outboxRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getAggregateType()).isEqualTo("User");
		assertThat(found.getAggregateId()).isEqualTo(aggregateId);
		assertThat(found.getEventType()).isEqualTo("UserRegistered");
		assertThat(found.getPayload()).isEqualTo(payload);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getPublishedAt()).isNull();
	}

	private static User newUser(String email) {
		return new User(email, "hash", "Test", "User");
	}

	private static Address newDefaultAddress(User user, String line1) {
		Address address = new Address(user, "Test User", "5550000000", line1, "Istanbul");
		address.setDefault(true);
		return address;
	}

}
