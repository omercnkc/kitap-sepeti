package com.kitapsepeti.user.outbox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;

/** Outbox'a sırası belli (created_at 1 sn arayla) yayınlanmamış UserRegistered satırları ekler. */
final class OutboxTestRows {

	private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

	private OutboxTestRows() {
	}

	static List<UUID> newIds(int count) {
		return Stream.generate(UUID::randomUUID).limit(count).toList();
	}

	/** Tek INSERT: satırlar worker'a aynı anda görünür. created_at sırası {@code ids} sırasıdır. */
	static void insert(JdbcTemplate jdbc, List<UUID> ids) {
		StringBuilder sql = new StringBuilder(
				"INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, payload, created_at) VALUES ");
		List<Object> args = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			UUID userId = UUID.randomUUID();
			sql.append(i == 0 ? "" : ", ").append("(UUID_TO_BIN(?), 'user', UUID_TO_BIN(?), 'UserRegistered', ?, ?)");
			args.add(ids.get(i).toString());
			args.add(userId.toString());
			args.add("""
					{"eventVersion":1,"userId":"%s","email":"sira-%d@example.com","firstName":"Ali","occurredAt":"2026-01-01T00:00:00Z"}"""
				.formatted(userId, i));
			args.add(BASE_TIME.plusSeconds(i));
		}
		jdbc.update(sql.toString(), args.toArray());
	}

}
