package com.kitapsepeti.catalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.support.InternalTestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Internal stok testlerinin ortak yardımcıları; saat her testte {@link #NOW}'a sabitlenir. */
abstract class InternalStockTestSupport extends ApiTestSupport {

	static final String BASE = "/internal/stock/reservations";

	static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	@Autowired
	protected JsonMapper jsonMapper;

	private int bookCounter;

	@BeforeEach
	void prepareFixtures() {
		clock.fixAt(NOW);
	}

	/** Olay üretmeden doğrudan DB'ye kitap ekler (yazar/kategori yok). */
	UUID book(String status, int stock) {
		return book(status, stock, new BigDecimal("149.90"));
	}

	UUID book(String status, int stock, BigDecimal price) {
		UUID id = UUID.randomUUID();
		bookCounter++;
		jdbc.update("INSERT INTO books (id, title, price_amount, currency, stock_quantity, status, "
				+ "published_at) VALUES (UUID_TO_BIN(?), ?, ?, 'TRY', ?, ?, ?)", id.toString(),
				"Kitap " + bookCounter, price, stock, status,
				"draft".equals(status) ? null : Timestamp.from(NOW.minusSeconds(3600)));
		return id;
	}

	static Map<String, Object> request(UUID orderId, Object... bookIdAndQuantity) {
		List<Map<String, Object>> items = new ArrayList<>();
		for (int i = 0; i < bookIdAndQuantity.length; i += 2) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("bookId", bookIdAndQuantity[i]);
			item.put("quantity", bookIdAndQuantity[i + 1]);
			items.add(item);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("orderId", orderId);
		body.put("items", items);
		return body;
	}

	ResultActions reserve(Object body) throws Exception {
		return mockMvc.perform(post(BASE).with(InternalTestKeys.orderServiceKey())
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonMapper.writeValueAsString(body)));
	}

	ResultActions commit(UUID orderId) throws Exception {
		return mockMvc.perform(post(BASE + "/" + orderId + "/commit").with(InternalTestKeys.orderServiceKey()));
	}

	ResultActions release(UUID orderId) throws Exception {
		return mockMvc.perform(post(BASE + "/" + orderId + "/release").with(InternalTestKeys.orderServiceKey()));
	}

	ResultActions fetch(UUID orderId) throws Exception {
		return mockMvc.perform(get(BASE + "/" + orderId).with(InternalTestKeys.orderServiceKey()));
	}

	int reservedOf(UUID bookId) {
		return intColumn("reserved_quantity", bookId);
	}

	int stockOf(UUID bookId) {
		return intColumn("stock_quantity", bookId);
	}

	long versionOf(UUID bookId) {
		return jdbc.queryForObject("SELECT version FROM books WHERE id = UUID_TO_BIN(?)", Long.class,
				bookId.toString());
	}

	/** Siparişin rezervasyon satırları: book_id, quantity, status, expires_at (UTC, "yyyy-MM-dd HH:mm:ss.ffffff"). */
	List<Map<String, Object>> rowsOf(UUID orderId) {
		return jdbc.queryForList("SELECT BIN_TO_UUID(book_id) AS book_id, quantity, status, "
				+ "CAST(expires_at AS CHAR) AS expires_at "
				+ "FROM stock_reservations WHERE order_id = UUID_TO_BIN(?) ORDER BY book_id", orderId.toString());
	}

	int reservationCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM stock_reservations", Integer.class);
	}

	List<Map<String, Object>> outbox() {
		return jdbc.queryForList("SELECT BIN_TO_UUID(aggregate_id) AS aggregate_id, event_type, "
				+ "CAST(payload AS CHAR) AS payload FROM outbox ORDER BY created_at, id");
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> payload(Map<String, Object> outboxRow) {
		return jsonMapper.readValue(outboxRow.get("payload").toString(), Map.class);
	}

	/** Görevler ayrı thread'lerde; hepsi hazır olunca tek latch ile aynı anda başlatılır. Sonuçlar görev sırasıyla. */
	static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private int intColumn(String column, UUID bookId) {
		return jdbc.queryForObject("SELECT " + column + " FROM books WHERE id = UUID_TO_BIN(?)", Integer.class,
				bookId.toString());
	}

}
