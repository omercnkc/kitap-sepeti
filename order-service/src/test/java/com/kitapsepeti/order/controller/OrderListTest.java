package com.kitapsepeti.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.entity.OrderStatus;
import com.kitapsepeti.order.repository.OrderRepository;
import com.kitapsepeti.order.support.SqlCapture;
import com.kitapsepeti.order.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * {@code GET /api/orders}: kullanıcının kendi siparişlerinin sayfalı özeti.
 * Catalog liste ucuyla birebir aynı sayfalama kuralları (size en fazla 50, geçersiz → 400 VALIDATION_FAILED).
 * Sıralama sabit: {@code created_at DESC, id DESC}; sort parametresi yok.
 * Doğrudan SQL ile yazma yok (domain ve JPA üzerinden kurulur).
 */
class OrderListTest extends CheckoutTestSupport {

	private static final Instant BASE_TIME = Instant.parse("2026-03-01T10:00:00Z");

	private static final AddressSnapshot ADDRESS = new AddressSnapshot("Alıcı Ad", "+905550000000",
			"Sokak 1", null, null, "Ankara", null, "TR");

	private static final Comparator<UUID> UNSIGNED_UUID_ORDER = Comparator
		.comparing((UUID u) -> u.getMostSignificantBits(), Long::compareUnsigned)
		.thenComparing(u -> u.getLeastSignificantBits(), Long::compareUnsigned);

	@Autowired
	private OrderRepository orderRepository;

	@BeforeEach
	void clearSqlCapture() {
		SqlCapture.stop();
	}

	@Test
	void emptyListReturns200WithEmptyItemsAndCorrectPageMetadata() throws Exception {
		mockMvc.perform(get("/api/orders").with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items", empty()))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void sortingIsCreatedAtDescThenIdDesc() throws Exception {
		// Farklı zamanlarda oluşturulmuş siparişler
		Instant t1 = BASE_TIME.minus(3, ChronoUnit.HOURS);
		Instant t2 = BASE_TIME.minus(2, ChronoUnit.HOURS);
		Instant t3 = BASE_TIME.minus(1, ChronoUnit.HOURS);
		// Aynı zamanda oluşturulmuş iki sipariş (id DESC eşitlik bozma)
		Instant t4 = BASE_TIME;

		Order o1 = createOrder(this.userId, t1, 1, OrderStatus.PAID);
		Order o2 = createOrder(this.userId, t2, 1, OrderStatus.FAILED);
		Order o3 = createOrder(this.userId, t3, 1, OrderStatus.PAID);
		Order o4a = createOrder(this.userId, t4, 1, OrderStatus.FAILED);
		Order o4b = createOrder(this.userId, t4, 1, OrderStatus.PENDING);

		// t4'teki iki siparişin id'lerini unsigned desc sırala
		Order firstT4 = UNSIGNED_UUID_ORDER.compare(o4a.getId(), o4b.getId()) > 0 ? o4a : o4b;
		Order secondT4 = firstT4 == o4a ? o4b : o4a;

		mockMvc.perform(get("/api/orders?page=0&size=10").with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", contains(
					firstT4.getId().toString(),
					secondT4.getId().toString(),
					o3.getId().toString(),
					o2.getId().toString(),
					o1.getId().toString())))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void paginationLimitsLastPageAndOutOfRangeBehaviorMatchCatalog() throws Exception {
		List<Order> created = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Instant time = BASE_TIME.minus(i, ChronoUnit.HOURS);
			created.add(createOrder(this.userId, time, 1, i == 0 ? OrderStatus.PENDING : OrderStatus.PAID));
		}

		// İlk sayfa: 0, size 2 -> 2 öğe
		mockMvc.perform(get("/api/orders?page=0&size=2").with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].id").value(created.get(0).getId().toString()))
			.andExpect(jsonPath("$.items[1].id").value(created.get(1).getId().toString()))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3));

		// Son sayfa: 2, size 2 -> 1 öğe
		mockMvc.perform(get("/api/orders?page=2&size=2").with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].id").value(created.get(4).getId().toString()))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3));

		// Aralık dışı sayfa: 3, size 2 -> boş items, toplamlar aynı
		mockMvc.perform(get("/api/orders?page=3&size=2").with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", empty()))
			.andExpect(jsonPath("$.page").value(3))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void anotherUsersOrdersAreNeverVisibleAndTotalElementsOnlyCountsOwnOrders() throws Exception {
		UUID otherUser = UUID.randomUUID();
		String otherToken = TestJwt.user(otherUser.toString());

		createOrder(this.userId, BASE_TIME.minus(1, ChronoUnit.HOURS), 1, OrderStatus.PAID);
		createOrder(this.userId, BASE_TIME, 1, OrderStatus.PENDING);

		createOrder(otherUser, BASE_TIME.minus(2, ChronoUnit.HOURS), 1, OrderStatus.PAID);
		createOrder(otherUser, BASE_TIME.minus(1, ChronoUnit.HOURS), 1, OrderStatus.FAILED);
		createOrder(otherUser, BASE_TIME, 1, OrderStatus.PENDING);

		mockMvc.perform(get("/api/orders").with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items", hasSize(2)));

		mockMvc.perform(get("/api/orders").with(bearer(otherToken)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.items", hasSize(3)));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"size=51   | size",
			"size=0    | size",
			"size=-1   | size",
			"size=metin| size",
			"page=-1   | page",
			"page=metin| page"
	})
	void invalidPageAndSizeParametersReturn400ValidationFailed(String query, String field) throws Exception {
		String body = mockMvc.perform(get("/api/orders?" + query).with(bearer(this.token)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains(field)))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body)
			.doesNotContain("Exception")
			.doesNotContain("metin")
			.doesNotContain("51");
	}

	@Test
	void withoutTokenReturns401Unauthorized() throws Exception {
		mockMvc.perform(get("/api/orders").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void itemFieldsAreCorrectAndForbiddenFieldsAreOmitted() throws Exception {
		Order multiItemOrder = createOrder(this.userId, BASE_TIME.minus(1, ChronoUnit.HOURS), 3, OrderStatus.PAID);
		Order failedOrder = createOrder(this.userId, BASE_TIME, 1, OrderStatus.FAILED);

		mockMvc.perform(get("/api/orders").with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			// Failed sipariş
			.andExpect(jsonPath("$.items[0].id").value(failedOrder.getId().toString()))
			.andExpect(jsonPath("$.items[0].status").value("failed"))
			.andExpect(jsonPath("$.items[0].failureCode").value("ORDER_EXPIRED"))
			.andExpect(jsonPath("$.items[0].currency").value("TRY"))
			.andExpect(jsonPath("$.items[0].totalAmount").value(50.00))
			.andExpect(jsonPath("$.items[0].itemCount").value(1))
			.andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty())
			// Paid sipariş (3 kalemli)
			.andExpect(jsonPath("$.items[1].id").value(multiItemOrder.getId().toString()))
			.andExpect(jsonPath("$.items[1].status").value("paid"))
			.andExpect(jsonPath("$.items[1].failureCode", is(nullValue())))
			.andExpect(jsonPath("$.items[1].currency").value("TRY"))
			.andExpect(jsonPath("$.items[1].totalAmount").value(150.00))
			.andExpect(jsonPath("$.items[1].itemCount").value(3))
			// Yasak alanlar öğe düzeyinde YOK
			.andExpect(jsonPath("$.items[0].address").doesNotExist())
			.andExpect(jsonPath("$.items[0].addressSnapshot").doesNotExist())
			.andExpect(jsonPath("$.items[0].items").doesNotExist())
			.andExpect(jsonPath("$.items[0].stockState").doesNotExist())
			.andExpect(jsonPath("$.items[0].paymentId").doesNotExist())
			.andExpect(jsonPath("$.items[0].latePaymentAt").doesNotExist())
			.andExpect(jsonPath("$.items[0].userId").doesNotExist())
			.andExpect(jsonPath("$.items[0].cartId").doesNotExist())
			.andExpect(jsonPath("$.items[0].subtotal").doesNotExist())
			.andExpect(jsonPath("$.items[0].discountAmount").doesNotExist())
			.andExpect(jsonPath("$.items[0].history").doesNotExist());
	}

	@Test
	void queryCountIsIdenticalForOneAndTwentyItemPagesWithoutNPlusOne() throws Exception {
		UUID user1 = UUID.randomUUID();
		UUID user20 = UUID.randomUUID();
		String token1 = TestJwt.user(user1.toString());
		String token20 = TestJwt.user(user20.toString());

		// 1 siparişli kullanıcı: 2 sipariş yazıp size=1 ile 1 öğeli sayfa çekilir
		createOrder(user1, BASE_TIME, 2, OrderStatus.PAID);
		createOrder(user1, BASE_TIME.minus(1, ChronoUnit.HOURS), 2, OrderStatus.FAILED);

		// 20 siparişli kullanıcı: 25 sipariş yazıp size=20 ile 20 öğeli sayfa çekilir
		for (int i = 0; i < 25; i++) {
			createOrder(user20, BASE_TIME.minus(i, ChronoUnit.MINUTES), 2,
					i == 0 ? OrderStatus.PENDING : OrderStatus.PAID);
		}

		// 1 öğeli sayfa sorgu sayısı (1 select + 1 count)
		SqlCapture.start();
		mockMvc.perform(get("/api/orders?page=0&size=1").with(bearer(token1)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)));
		List<String> statements1 = SqlCapture.stop();

		// 20 öğeli sayfa sorgu sayısı (1 select + 1 count)
		SqlCapture.start();
		mockMvc.perform(get("/api/orders?page=0&size=20").with(bearer(token20)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(20)));
		List<String> statements2 = SqlCapture.stop();

		assertThat(statements1.size()).as("SQL sorgu sayısı (1 öğe)").isEqualTo(2);
		assertThat(statements2.size()).as("SQL sorgu sayısı (20 öğe)").isEqualTo(2);
		assertThat(statements1.size()).as("1 ve 20 öğeli sayfalarda sorgu sayısı aynı olmalı")
			.isEqualTo(statements2.size());
	}

	@Test
	void explainShowsQueryUsesUserCreatedIndexWithoutFilesort() {
		// Dağılım için birkaç sipariş oluştur
		createOrder(this.userId, BASE_TIME.minus(2, ChronoUnit.HOURS), 1, OrderStatus.PAID);
		createOrder(this.userId, BASE_TIME.minus(1, ChronoUnit.HOURS), 2, OrderStatus.FAILED);
		createOrder(this.userId, BASE_TIME, 1, OrderStatus.PENDING);

		this.jdbc.queryForList("ANALYZE TABLE orders");

		// Liste sorgusu EXPLAIN
		List<Map<String, Object>> listPlan = this.jdbc.queryForList("""
				EXPLAIN SELECT o.id, o.status, o.failure_code, o.currency, o.total_amount,
				    (SELECT COUNT(i.id) FROM order_items i WHERE i.order_id = o.id) AS item_count,
				    o.created_at, o.updated_at
				FROM orders o
				WHERE o.user_id = UUID_TO_BIN(?)
				ORDER BY o.created_at DESC, o.id DESC
				LIMIT 20
				""", this.userId.toString());

		assertThat(listPlan).isNotEmpty();
		Map<String, Object> orderTablePlan = listPlan.stream()
			.filter(row -> "o".equals(row.get("table")))
			.findFirst()
			.orElseThrow();
		assertThat(orderTablePlan.get("key")).isEqualTo("ix_orders_user_created");
		assertThat(String.valueOf(orderTablePlan.get("Extra"))).doesNotContain("filesort");

		// Sayım sorgusu EXPLAIN
		List<Map<String, Object>> countPlan = this.jdbc.queryForList("""
				EXPLAIN SELECT COUNT(o.id)
				FROM orders o
				WHERE o.user_id = UUID_TO_BIN(?)
				""", this.userId.toString());

		assertThat(countPlan).isNotEmpty();
		assertThat(countPlan.get(0).get("key")).isEqualTo("ix_orders_user_created");
	}

	private Order createOrder(UUID user, Instant createdAt, int itemCount, OrderStatus targetStatus) {
		List<OrderLine> lines = new ArrayList<>();
		for (int i = 1; i <= itemCount; i++) {
			lines.add(new OrderLine(UUID.randomUUID(), "Kitap " + i, 1, new BigDecimal("50.00")));
		}
		Clock clock = Clock.fixed(createdAt, ZoneOffset.UTC);
		Order order = Order.place(user, UUID.randomUUID(), "TRY", lines, ADDRESS, clock);
		if (targetStatus == OrderStatus.PAID) {
			order.markStockHeld(clock);
			order.markPaid(UUID.randomUUID(), clock);
		} else if (targetStatus == OrderStatus.FAILED) {
			order.markFailed("ORDER_EXPIRED", clock);
		}
		return this.orderRepository.saveAndFlush(order);
	}

}
