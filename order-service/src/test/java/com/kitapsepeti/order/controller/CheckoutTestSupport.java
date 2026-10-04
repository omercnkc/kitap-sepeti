package com.kitapsepeti.order.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.order.ApiTestSupport;
import com.kitapsepeti.order.client.Downstream;
import com.kitapsepeti.order.client.DownstreamCircuitBreakers;
import com.kitapsepeti.order.support.MutableClock;
import com.kitapsepeti.order.support.StubServer;
import com.kitapsepeti.order.support.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Checkout ve sipariş okuma testlerinin tabanı: Cart, Catalog ve Payment yerine WireMock, gerçek MySQL. Her testten
 * önce sunucular açık ve boş, circuit breaker'lar kapalı ve sıfır, saat gerçek; her test kendi kullanıcısıyla çalışır
 * (bekleyen sipariş kuralı testler arasında çakışmaz).
 */
abstract class CheckoutTestSupport extends ApiTestSupport {

	static final String CHECKOUT = "/api/orders/checkout";

	static final String SNAPSHOT = "/internal/cart/snapshot";

	static final String LOOKUP = "/api/books/lookup";

	static final String RESERVATIONS = "/internal/stock/reservations";

	static final String PAYMENTS = "/internal/payments";

	/** Adres değerleri logda aranır; başka hiçbir metinde geçmeyecek kadar özgün. */
	static final String RECIPIENT = "Gizlialici Soyadxq";

	static final String PHONE = "+905559876543";

	static final String LINE1 = "Saklisokak No 77";

	static final String LINE2 = "Kat 9 Daire 31";

	static final String DISTRICT = "Gizliilce";

	static final String CITY = "Saklisehir";

	static final String POSTAL_CODE = "26999";

	static final String ADDRESS_JSON = """
			{"recipientName":"%s","phone":"%s","line1":"%s","line2":"%s","district":"%s","city":"%s",\
			"postalCode":"%s","country":"TR"}""".formatted(RECIPIENT, PHONE, LINE1, LINE2, DISTRICT, CITY, POSTAL_CODE);

	static final String CHECKOUT_BODY = "{\"address\":" + ADDRESS_JSON + "}";

	static final List<StubServer> STUBS = List.of(CART, CATALOG, PAYMENT);

	@Autowired
	DownstreamCircuitBreakers breakers;

	@Autowired
	MutableClock clock;

	UUID userId;

	String token;

	record Book(UUID id, String title, String price, String currency, boolean inStock) {

		static Book of(String title, String price) {
			return new Book(UUID.randomUUID(), title, price, "TRY", true);
		}

		Book withCurrency(String newCurrency) {
			return new Book(this.id, this.title, this.price, newCurrency, this.inStock);
		}

		Book outOfStock() {
			return new Book(this.id, this.title, this.price, this.currency, false);
		}

		String json() {
			return """
					{"id":"%s","title":"%s","priceAmount":%s,"currency":"%s","inStock":%s,"slug":"yok-sayilir"}"""
				.formatted(this.id, this.title, this.price, this.currency, this.inStock);
		}

	}

	record Line(Book book, int quantity) {
	}

	@BeforeEach
	void resetRemotesAndUser() {
		for (StubServer stub : STUBS) {
			stub.ensureRunning();
			stub.server().resetAll();
		}
		for (Downstream downstream : Downstream.values()) {
			this.breakers.get(downstream).reset();
		}
		this.clock.reset();
		this.userId = UUID.randomUUID();
		this.token = TestJwt.user(this.userId.toString());
	}

	@AfterEach
	void restoreRemotes() {
		STUBS.forEach(StubServer::ensureRunning);
		for (Downstream downstream : Downstream.values()) {
			this.breakers.get(downstream).reset();
		}
		this.clock.reset();
	}

	ResultActions checkout() throws Exception {
		return checkout(this.token, CHECKOUT_BODY);
	}

	ResultActions checkout(String bearerToken, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(CHECKOUT)
			.with(bearer(bearerToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	// --- Stub'lar ---

	/** Sepet: kitaplar ve adetler (Cart'ın tüm alanlarıyla; Order yalnızca cartId + bookId/quantity okur). */
	static UUID stubCart(Line... lines) {
		UUID cartId = UUID.randomUUID();
		String items = List.of(lines)
			.stream()
			.map(line -> """
					{"bookId":"%s","quantity":%d,"unitPriceSnapshot":1.00,"currencySnapshot":"TRY"}"""
				.formatted(line.book().id(), line.quantity()))
			.collect(Collectors.joining(","));
		CART.server().stubFor(post(urlEqualTo(SNAPSHOT)).willReturn(json(200, """
				{"cartId":"%s","updatedAt":"2026-10-04T10:00:00Z","items":[%s]}""".formatted(cartId, items))));
		return cartId;
	}

	static void stubCart(ResponseDefinitionBuilder response) {
		CART.server().stubFor(post(urlEqualTo(SNAPSHOT)).willReturn(response));
	}

	/** Catalog lookup: yalnızca verilen kitaplar yanıtta (diğerleri "bulunamadı"). */
	static void stubLookup(Book... books) {
		stubLookup(json(200, "{\"items\":[" + List.of(books).stream().map(Book::json).collect(Collectors.joining(","))
				+ "]}"));
	}

	static void stubLookup(ResponseDefinitionBuilder response) {
		CATALOG.server().stubFor(get(urlPathEqualTo(LOOKUP)).willReturn(response));
	}

	/** Rezervasyon {@code held}: yanıttaki orderId istekteki sipariş (Catalog sözleşmesi). */
	static void stubReserveHeld() {
		stubReserve(reservation(201, "held"));
	}

	static void stubReserve(ResponseDefinitionBuilder response) {
		CATALOG.server().stubFor(post(urlEqualTo(RESERVATIONS)).willReturn(response));
	}

	static ResponseDefinitionBuilder reservation(int status, String reservationStatus) {
		return json(status, """
				{"orderId":"{{jsonPath request.body '$.orderId'}}","status":"%s","expiresAt":"2026-10-04T10:15:00Z",\
				"items":[]}""".formatted(reservationStatus)).withTransformers("response-template");
	}

	/** Payment 201: verilen ödeme id'siyle, istekteki siparişe ait. */
	static void stubPaymentInitiated(UUID paymentId, String paymentStatus) {
		stubPayment(json(201, """
				{"paymentId":"%s","orderId":"{{jsonPath request.body '$.orderId'}}","status":"%s","amount":1.00,\
				"currency":"TRY","failureCode":null,"redirectUrl":null}""".formatted(paymentId, paymentStatus))
			.withTransformers("response-template"));
	}

	static void stubPayment(ResponseDefinitionBuilder response) {
		PAYMENT.server().stubFor(post(urlEqualTo(PAYMENTS)).willReturn(response));
	}

	/** Mutlu yol: tek kitap, stok tutuldu, ödeme başlatıldı. */
	static Book stubHappyPath() {
		Book book = Book.of("Mutlu Kitap", "149.90");
		stubCart(new Line(book, 1));
		stubLookup(book);
		stubReserveHeld();
		stubPaymentInitiated(UUID.randomUUID(), "initiated");
		return book;
	}

	static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	static ResponseDefinitionBuilder problem(int status, String code) {
		return aResponse().withStatus(status)
			.withHeader("Content-Type", "application/problem+json")
			.withBody("""
					{"type":"about:blank","title":"t","status":%d,"code":"%s","instance":"/x"}""".formatted(status, code));
	}

	static ResponseDefinitionBuilder stockProblem(String code, UUID bookId) {
		return aResponse().withStatus(409)
			.withHeader("Content-Type", "application/problem+json")
			.withBody("""
					{"title":"t","status":409,"code":"%s","instance":"%s","bookIds":["%s"]}"""
				.formatted(code, RESERVATIONS, bookId));
	}

	// --- İstek kayıtları ---

	static List<LoggedRequest> reserveRequests() {
		return CATALOG.server().findAll(postRequestedFor(urlEqualTo(RESERVATIONS)));
	}

	static List<LoggedRequest> paymentRequests() {
		return PAYMENT.server().findAll(postRequestedFor(urlEqualTo(PAYMENTS)));
	}

	static List<LoggedRequest> cartRequests() {
		return CART.server().findAll(anyRequestedFor(anyUrl()));
	}

	static List<LoggedRequest> allDownstreamRequests() {
		return STUBS.stream().flatMap(stub -> stub.server().findAll(anyRequestedFor(anyUrl())).stream()).toList();
	}

	// --- DB ---

	int orderCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = UUID_TO_BIN(?)", Integer.class,
				this.userId.toString());
	}

	/** Küçük harfli takma adlar: satır map'i anahtarları JVM locale'iyle küçültür (tr-TR tuzağı). */
	Map<String, Object> orderRow(UUID orderId) {
		return jdbc.queryForMap("""
				SELECT BIN_TO_UUID(user_id) AS u, BIN_TO_UUID(cart_id) AS c, status AS s, stock_state AS st,
					BIN_TO_UUID(payment_id) AS p, failure_code AS f, currency AS cur, subtotal AS sub,
					discount_amount AS disc, total_amount AS tot
				FROM orders WHERE id = UUID_TO_BIN(?)""", orderId.toString());
	}

	List<Map<String, Object>> historyRows(UUID orderId) {
		return jdbc.queryForList("""
				SELECT from_status AS fs, to_status AS ts, reason AS r FROM order_status_history
				WHERE order_id = UUID_TO_BIN(?) ORDER BY created_at, id""", orderId.toString());
	}

	List<Map<String, Object>> itemRows(UUID orderId) {
		return jdbc.queryForList("""
				SELECT BIN_TO_UUID(book_id) AS b, title_snapshot AS t, quantity AS q, unit_price AS up, line_total AS lt
				FROM order_items WHERE order_id = UUID_TO_BIN(?) ORDER BY id""", orderId.toString());
	}

	static UUID orderIdOf(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.orderId"));
	}

	static UUID idOf(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

}
