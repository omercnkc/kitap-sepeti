package com.kitapsepeti.catalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Internal stok rezervasyon uçları: oluşturma, ya hep ya hiç, idempotency, onay/iptal, olaylar. */
class InternalStockControllerTest extends InternalStockTestSupport {

	// --- 4. Yeni rezervasyon

	@Test
	void reservesTwoBooksAndReturns201WithSnapshot() throws Exception {
		UUID a = book("published", 5, new BigDecimal("149.90"));
		UUID b = book("published", 3, new BigDecimal("89.50"));
		UUID orderId = UUID.randomUUID();

		reserve(request(orderId, a, 2, b, 1)).andExpect(status().isCreated())
			.andExpect(header().string("Location", BASE + "/" + orderId))
			.andExpect(jsonPath("$.orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.status").value("held"))
			.andExpect(jsonPath("$.expiresAt").value("2026-01-01T10:15:00Z"))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[?(@.bookId=='" + a + "')].quantity").value(2))
			.andExpect(jsonPath("$.items[?(@.bookId=='" + a + "')].unitPrice").value("149.90"))
			.andExpect(jsonPath("$.items[?(@.bookId=='" + a + "')].currency").value("TRY"))
			.andExpect(jsonPath("$.items[?(@.bookId=='" + b + "')].quantity").value(1))
			.andExpect(jsonPath("$.items[?(@.bookId=='" + b + "')].unitPrice").value("89.50"))
			.andExpect(jsonPath("$.items[*].title", containsInAnyOrder("Kitap 1", "Kitap 2")));

		assertThat(reservedOf(a)).isEqualTo(2);
		assertThat(reservedOf(b)).isEqualTo(1);
		assertThat(stockOf(a)).isEqualTo(5);
		assertThat(versionOf(a)).isZero();
		List<Map<String, Object>> rows = rowsOf(orderId);
		assertThat(rows).hasSize(2);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("status")).isEqualTo("held");
			assertThat(row.get("expires_at")).isEqualTo("2026-01-01 10:15:00.000000");
		});
		assertThat(rows).extracting(row -> row.get("quantity")).containsExactlyInAnyOrder(2, 1);
		// Satılabilir stok tükenmedi; inStock değişmediği için olay yok.
		assertThat(outbox()).isEmpty();
	}

	// --- 5. Ya hep ya hiç

	@Test
	void oneInsufficientBookRejectsWholeOrderAndReportsOnlyThatBook() throws Exception {
		UUID enough = book("published", 5);
		UUID scarce = book("published", 1);
		UUID orderId = UUID.randomUUID();

		reserve(request(orderId, enough, 2, scarce, 2)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
			.andExpect(jsonPath("$.bookIds", contains(scarce.toString())));

		assertThat(reservedOf(enough)).isZero();
		assertThat(reservedOf(scarce)).isZero();
		assertThat(reservationCount()).isZero();
		assertThat(outbox()).isEmpty();
	}

	// --- 6. Hata nedenleri

	@Test
	void everyFailingBookIsReported() throws Exception {
		UUID a = book("published", 1);
		UUID b = book("published", 1);

		reserve(request(UUID.randomUUID(), a, 2, b, 2)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
			.andExpect(jsonPath("$.bookIds", containsInAnyOrder(a.toString(), b.toString())));
		assertThat(reservedOf(a)).isZero();
		assertThat(reservedOf(b)).isZero();
	}

	@Test
	void draftArchivedOrMissingBookIsNotAvailable() throws Exception {
		UUID draft = book("draft", 5);
		UUID archived = book("archived", 5);
		UUID missing = UUID.randomUUID();
		UUID ok = book("published", 5);

		for (UUID unavailable : List.of(draft, archived, missing)) {
			reserve(request(UUID.randomUUID(), ok, 1, unavailable, 1)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BOOK_NOT_AVAILABLE"))
				.andExpect(jsonPath("$.bookIds", contains(unavailable.toString())));
		}
		assertThat(reservedOf(ok)).isZero();
		assertThat(reservedOf(draft)).isZero();
		assertThat(reservationCount()).isZero();
	}

	@Test
	void notAvailableWinsOverInsufficientAndListsOnlyItsBooks() throws Exception {
		UUID draft = book("draft", 5);
		UUID scarce = book("published", 1);

		reserve(request(UUID.randomUUID(), draft, 1, scarce, 3)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOOK_NOT_AVAILABLE"))
			.andExpect(jsonPath("$.bookIds", contains(draft.toString())));
	}

	// --- 7. Doğrulama

	@Test
	void invalidRequestsReturn400() throws Exception {
		UUID a = book("published", 5);
		UUID orderId = UUID.randomUUID();

		assertInvalid(request(orderId), "items");
		assertInvalid(request(orderId, a, 1, a, 2), "items");
		assertInvalid(request(orderId, a, 0), "items[0].quantity");
		assertInvalid(request(orderId, a, 101), "items[0].quantity");
		Object[] tooMany = new Object[102];
		for (int i = 0; i < 51; i++) {
			tooMany[2 * i] = UUID.randomUUID();
			tooMany[2 * i + 1] = 1;
		}
		assertInvalid(request(orderId, tooMany), "items");
		assertInvalid(request(null, a, 1), "orderId");
		assertInvalid(request(orderId, null, 1), "items[0].bookId");

		assertThat(reservationCount()).isZero();
		assertThat(reservedOf(a)).isZero();
	}

	// --- 8. Idempotency

	@Test
	void sameRequestTwiceReturnsSameReservationAndReservesOnce() throws Exception {
		UUID a = book("published", 5);
		UUID b = book("published", 5);
		UUID orderId = UUID.randomUUID();

		String first = reserve(request(orderId, a, 2, b, 1)).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		clock.advance(Duration.ofMinutes(3));
		String second = reserve(request(orderId, b, 1, a, 2)).andExpect(status().isOk())
			.andExpect(header().doesNotExist("Location"))
			.andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(reservedOf(a)).isEqualTo(2);
		assertThat(reservedOf(b)).isEqualTo(1);
		assertThat(rowsOf(orderId)).hasSize(2);
	}

	@Test
	void differentItemsForExistingOrderAreRejected() throws Exception {
		UUID a = book("published", 5);
		UUID b = book("published", 5);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, a, 2)).andExpect(status().isCreated());

		reserve(request(orderId, a, 3)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_MISMATCH"));
		reserve(request(orderId, a, 2, b, 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_MISMATCH"));

		assertThat(reservedOf(a)).isEqualTo(2);
		assertThat(reservedOf(b)).isZero();
		assertThat(rowsOf(orderId)).hasSize(1);
	}

	// --- 12. Onay

	@Test
	void commitDeductsStockOnceWithoutEvent() throws Exception {
		UUID a = book("published", 5);
		UUID b = book("published", 4);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, a, 2, b, 1)).andExpect(status().isCreated());

		String first = commit(orderId).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("committed"))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andReturn().getResponse().getContentAsString();

		assertThat(stockOf(a)).isEqualTo(3);
		assertThat(reservedOf(a)).isZero();
		assertThat(stockOf(b)).isEqualTo(3);
		assertThat(reservedOf(b)).isZero();
		assertThat(versionOf(a)).isZero();
		assertThat(rowsOf(orderId)).extracting(row -> row.get("status")).containsOnly("committed");
		assertThat(outbox()).isEmpty();

		String again = commit(orderId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(again).isEqualTo(first);
		assertThat(stockOf(a)).isEqualTo(3);
		assertThat(stockOf(b)).isEqualTo(3);
	}

	// --- 13. Süresi geçmiş rezervasyon da onaylanır

	@Test
	void expiredHeldReservationCanStillBeCommitted() throws Exception {
		UUID a = book("published", 2);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, a, 1)).andExpect(status().isCreated());
		clock.advance(Duration.ofHours(2));

		commit(orderId).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("committed"))
			.andExpect(jsonPath("$.expiresAt").value("2026-01-01T10:15:00Z"));
		assertThat(stockOf(a)).isEqualTo(1);
		assertThat(reservedOf(a)).isZero();
	}

	// --- 14. İptal ve durum geçişleri

	@Test
	void releaseReturnsStockAndStateTransitionsAreEnforced() throws Exception {
		UUID a = book("published", 5);
		UUID released = UUID.randomUUID();
		reserve(request(released, a, 2)).andExpect(status().isCreated());

		String first = release(released).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("released"))
			.andReturn().getResponse().getContentAsString();
		assertThat(reservedOf(a)).isZero();
		assertThat(stockOf(a)).isEqualTo(5);
		assertThat(rowsOf(released)).extracting(row -> row.get("status")).containsOnly("released");

		assertThat(release(released).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
			.isEqualTo(first);
		assertThat(reservedOf(a)).isZero();
		commit(released).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_RELEASED"));
		assertThat(stockOf(a)).isEqualTo(5);

		UUID committed = UUID.randomUUID();
		reserve(request(committed, a, 1)).andExpect(status().isCreated());
		commit(committed).andExpect(status().isOk());
		release(committed).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_COMMITTED"));
		assertThat(stockOf(a)).isEqualTo(4);
		assertThat(reservedOf(a)).isZero();

		UUID missing = UUID.randomUUID();
		commit(missing).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		release(missing).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		fetch(missing).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	// --- 15. inStock olayları

	@Test
	void eventsAreWrittenOnlyWhenInStockFlips() throws Exception {
		UUID a = book("published", 1);
		UUID wide = book("published", 5);

		UUID partial = UUID.randomUUID();
		reserve(request(partial, wide, 2)).andExpect(status().isCreated());
		release(partial).andExpect(status().isOk());
		assertThat(outbox()).isEmpty();

		UUID first = UUID.randomUUID();
		reserve(request(first, a, 1)).andExpect(status().isCreated());
		assertThat(outbox()).hasSize(1);
		assertThat(outbox().get(0).get("aggregate_id")).isEqualTo(a.toString());
		assertThat(outbox().get(0).get("event_type")).isEqualTo("BookUpserted");
		assertThat(payload(outbox().get(0)).get("inStock")).isEqualTo(false);

		release(first).andExpect(status().isOk());
		assertThat(outbox()).hasSize(2);
		assertThat(payload(outbox().get(1)).get("inStock")).isEqualTo(true);

		UUID second = UUID.randomUUID();
		reserve(request(second, a, 1)).andExpect(status().isCreated());
		assertThat(outbox()).hasSize(3);
		assertThat(payload(outbox().get(2)).get("inStock")).isEqualTo(false);
		commit(second).andExpect(status().isOk());
		assertThat(outbox()).hasSize(3);
		commit(second).andExpect(status().isOk());
		assertThat(outbox()).hasSize(3);
	}

	// --- 16. Admin stok düzeltmesi rezervin altına inemez

	@Test
	void adminCannotAdjustStockBelowReserved() throws Exception {
		UUID a = book("published", 3);
		reserve(request(UUID.randomUUID(), a, 2)).andExpect(status().isCreated());

		adjust(a, -2).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STOCK_BELOW_RESERVED"));
		assertThat(stockOf(a)).isEqualTo(3);
		assertThat(reservedOf(a)).isEqualTo(2);

		adjust(a, -1).andExpect(status().isOk())
			.andExpect(jsonPath("$.stockQuantity").value(2))
			.andExpect(jsonPath("$.reservedQuantity").value(2))
			.andExpect(jsonPath("$.availableQuantity").value(0));
	}

	// --- 17. Okuma

	@Test
	void getReturnsCurrentState() throws Exception {
		UUID a = book("published", 5, new BigDecimal("12.00"));
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, a, 3)).andExpect(status().isCreated());

		fetch(orderId).andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.status").value("held"))
			.andExpect(jsonPath("$.expiresAt").value("2026-01-01T10:15:00Z"))
			.andExpect(jsonPath("$.items[0].bookId").value(a.toString()))
			.andExpect(jsonPath("$.items[0].title").value("Kitap 1"))
			.andExpect(jsonPath("$.items[0].quantity").value(3))
			.andExpect(jsonPath("$.items[0].unitPrice").value("12.00"))
			.andExpect(jsonPath("$.items[0].currency").value("TRY"));

		commit(orderId).andExpect(status().isOk());
		fetch(orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("committed"));
	}

	// --- yardımcılar

	private void assertInvalid(Map<String, Object> body, String field) throws Exception {
		reserve(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains(field)));
	}

	private ResultActions adjust(UUID id, int delta) throws Exception {
		return mockMvc.perform(post("/api/admin/books/" + id + "/stock-adjustments")
			.with(bearer(TestJwt.admin(SUBJECT)))
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonMapper.writeValueAsString(Map.of("delta", delta))));
	}

}
