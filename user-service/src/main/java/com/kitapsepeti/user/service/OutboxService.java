package com.kitapsepeti.user.service;

import java.util.UUID;

import com.kitapsepeti.user.entity.OutboxEvent;
import com.kitapsepeti.user.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Olayı iş verisiyle aynı transaction'da outbox tablosuna yazar. {@code MANDATORY}: açık bir
 * transaction yoksa hata verir; böylece olay, verisi kaydedilmeden (veya tersi) yazılamaz.
 */
@Service
public class OutboxService {

	private final OutboxRepository outboxRepository;

	private final JsonMapper jsonMapper;

	public OutboxService(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
		this.outboxRepository = outboxRepository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void append(String aggregateType, UUID aggregateId, String eventType, Object payload) {
		String json = jsonMapper.writeValueAsString(payload);
		outboxRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, json));
	}

}
