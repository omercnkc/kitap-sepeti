package com.kitapsepeti.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Olay id'si payload'dan önce üretilir; payload'ın eventId içerip içermemesi çağıranın kararı. */
class OutboxServiceTest {

	private final EntityManager entityManager = mock(EntityManager.class);

	private final OutboxService service = new OutboxService(this.entityManager, JsonMapper.builder().build());

	@Test
	void idIsGeneratedBeforePayloadAndEqualsPersistedRowId() {
		List<UUID> seenByFactory = new ArrayList<>();

		OutboxEvent event = this.service.append("payment", UUID.randomUUID(), "PaymentSucceeded", eventId -> {
			seenByFactory.add(eventId);
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("eventId", eventId);
			payload.put("amount", "149.90");
			return payload;
		});

		assertThat(seenByFactory).containsExactly(event.getId());
		assertThat(event.getId().version()).isEqualTo(7);
		assertThat(event.getPayload()).isEqualTo("{\"eventId\":\"" + event.getId() + "\",\"amount\":\"149.90\"}");
		assertThat(event.getAggregateType()).isEqualTo("payment");
		assertThat(event.getEventType()).isEqualTo("PaymentSucceeded");
		assertThat(event.getPublishedAt()).isNull();
		verify(this.entityManager).persist(event);
		verifyNoMoreInteractions(this.entityManager);
	}

	@Test
	void plainPayloadIsWrittenAsIsWithoutEventId() {
		UUID aggregateId = UUID.randomUUID();

		OutboxEvent event = this.service.append("user", aggregateId, "UserRegistered", Map.of("userId", aggregateId));

		assertThat(event.getPayload()).isEqualTo("{\"userId\":\"" + aggregateId + "\"}").doesNotContain("eventId");
		assertThat(event.getAggregateId()).isEqualTo(aggregateId);
		assertThat(event.getId()).isNotNull();
		verify(this.entityManager).persist(event);
	}

	@Test
	void bothOverloadsRequireAnOpenTransaction() throws Exception {
		for (Class<?> payloadType : List.of(Function.class, Object.class)) {
			Method append = OutboxService.class.getMethod("append", String.class, UUID.class, String.class, payloadType);
			Transactional transactional = append.getAnnotation(Transactional.class);
			assertThat(transactional).as(payloadType.getSimpleName()).isNotNull();
			assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
		}
	}

	@Test
	void constructorIdsAreTimeOrderedVersion7() {
		UUID first = new OutboxEvent("user", UUID.randomUUID(), "UserRegistered", "{}").getId();
		UUID second = OutboxEvent.newId();

		assertThat(first.version()).isEqualTo(7);
		assertThat(second.version()).isEqualTo(7);
		assertThat(second.compareTo(first)).isPositive();
	}

}
