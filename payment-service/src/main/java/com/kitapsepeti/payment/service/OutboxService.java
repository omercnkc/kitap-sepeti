package com.kitapsepeti.payment.service;

import java.util.UUID;
import java.util.function.Function;

import com.kitapsepeti.payment.entity.OutboxEvent;
import jakarta.persistence.EntityManager;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Olayı iş verisiyle aynı transaction'da outbox tablosuna yazar. {@code MANDATORY}: açık bir
 * transaction yoksa hata verir; böylece olay, verisi kaydedilmeden (veya tersi) yazılamaz.
 * <p>
 * Olay id'si önce üretilir (catalog'daki {@code @UuidGenerator(VERSION_7)} ile aynı üretici) ve payload'a
 * {@code eventId} olarak girer; atanmış id'li entity {@code merge}'e (fazladan SELECT) düşmesin diye
 * {@code persist} ile yazılır.
 */
@Service
public class OutboxService {

	private final EntityManager entityManager;

	private final JsonMapper jsonMapper;

	public OutboxService(EntityManager entityManager, JsonMapper jsonMapper) {
		this.entityManager = entityManager;
		this.jsonMapper = jsonMapper;
	}

	/** @param payloadFor olay id'sinden payload'ı kurar */
	@Transactional(propagation = Propagation.MANDATORY)
	public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType,
			Function<UUID, Object> payloadFor) {
		UUID eventId = UuidVersion7Strategy.INSTANCE.generateUuid(null);
		String json = jsonMapper.writeValueAsString(payloadFor.apply(eventId));
		OutboxEvent event = new OutboxEvent(eventId, aggregateType, aggregateId, eventType, json);
		entityManager.persist(event);
		return event;
	}

}
