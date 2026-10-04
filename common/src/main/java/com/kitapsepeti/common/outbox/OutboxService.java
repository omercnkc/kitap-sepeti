package com.kitapsepeti.common.outbox;

import java.util.UUID;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Olayı iş verisiyle aynı transaction'da outbox tablosuna yazar. {@code MANDATORY}: açık bir
 * transaction yoksa hata verir; böylece olay, verisi kaydedilmeden (veya tersi) yazılamaz.
 * <p>
 * Olay id'si payload kurulmadan önce üretilir; payload'ın {@code eventId} içerip içermemesi servisin kararıdır.
 * Atanmış id'li entity {@code merge}'e (fazladan SELECT) düşmesin diye {@code persist} ile yazılır.
 * Bean değil; servis {@link OutboxConfiguration}'ı import eder.
 */
public class OutboxService {

	private final EntityManager entityManager;

	private final JsonMapper jsonMapper;

	public OutboxService(EntityManager entityManager, JsonMapper jsonMapper) {
		this.entityManager = entityManager;
		this.jsonMapper = jsonMapper;
	}

	/** @param payloadFactory olay id'sinden payload'ı kurar (id payload'dan önce üretilir) */
	@Transactional(propagation = Propagation.MANDATORY)
	public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType,
			Function<UUID, ?> payloadFactory) {
		UUID eventId = OutboxEvent.newId();
		String json = this.jsonMapper.writeValueAsString(payloadFactory.apply(eventId));
		OutboxEvent event = new OutboxEvent(eventId, aggregateType, aggregateId, eventType, json);
		this.entityManager.persist(event);
		return event;
	}

	/** Payload olay id'sini içermiyorsa. */
	@Transactional(propagation = Propagation.MANDATORY)
	public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType, Object payload) {
		return append(aggregateType, aggregateId, eventType, eventId -> payload);
	}

}
