package com.kitapsepeti.cart.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.cart.ApiTestSupport;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.support.InternalTestKeys;
import com.kitapsepeti.cart.support.SqlCapture;
import com.kitapsepeti.cart.support.TestJwt;
import com.kitapsepeti.common.security.BearerChallenge;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code POST /internal/cart/snapshot}: internal anahtar zinciri, salt okunur görüntü ve hata/log hijyeni. */
@ExtendWith(OutputCaptureExtension.class)
class InternalCartSnapshotTest extends ApiTestSupport {

	private static final String SNAPSHOT = "/internal/cart/snapshot";

	private static final Instant T1 = Instant.parse("2026-03-01T10:15:30.123456Z");

	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void fixClock() {
		clock.fixAt(T1);
	}

	@Test
	void returnsActiveCartLinesInAddedOrderWithSnapshotPrices() throws Exception {
		Cart cart = Cart.openFor(userId, clock);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		cart.addItem(first, 2, new BigDecimal("145.00"), "TRY", "Birinci", null, clock);
		clock.advance(Duration.ofMinutes(1));
		cart.addItem(second, 1, new BigDecimal("7.50"), "TRY", "İkinci", "https://img.example/2.jpg", clock);
		clock.advance(Duration.ofMinutes(1));
		cart.addItem(third, 3, new BigDecimal("20.00"), "EUR", "Üçüncü", null, clock);
		carts.saveAndFlush(cart);

		String body = snapshot(userId).andExpect(status().isOk())
			.andExpect(jsonPath("$.cartId").value(cartId(userId)))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:17:30.123456Z"))
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[*].bookId").value(contains(first.toString(), second.toString(), third.toString())))
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andExpect(jsonPath("$.items[0].currency").value("TRY"))
			.andExpect(jsonPath("$.items[0].title").value("Birinci"))
			.andExpect(jsonPath("$.items[1].title").value("İkinci"))
			.andExpect(jsonPath("$.items[2].currency").value("EUR"))
			.andExpect(jsonPath("$.items[2].quantity").value(3))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("\"unitPriceSnapshot\":145.00", "\"unitPriceSnapshot\":7.50", "\"unitPriceSnapshot\":20.00")
			.doesNotContain("userId")
			.doesNotContain(userId.toString())
			.doesNotContain("coverUrl");
	}

	@Test
	void userWithoutCartGetsNullCartAndNoCartIsCreated() throws Exception {
		snapshot(userId).andExpect(status().isOk())
			.andExpect(jsonPath("$.cartId").value(nullValue()))
			.andExpect(jsonPath("$.updatedAt").value(nullValue()))
			.andExpect(jsonPath("$.items").isEmpty());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts", Integer.class)).isZero();
	}

	@Test
	void emptyActiveCartHasIdButNoItems() throws Exception {
		carts.saveAndFlush(Cart.openFor(userId, clock));

		snapshot(userId).andExpect(status().isOk())
			.andExpect(jsonPath("$.cartId").value(cartId(userId)))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:15:30.123456Z"))
			.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void closedCartsAreNotReturned() throws Exception {
		Cart checkedOut = Cart.openFor(userId, clock);
		checkedOut.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00"), "TRY", "Siparişte", null, clock);
		checkedOut.checkout(clock);
		carts.saveAndFlush(checkedOut);
		Cart abandoned = Cart.openFor(userId, clock);
		abandoned.abandon(clock);
		carts.saveAndFlush(abandoned);

		snapshot(userId).andExpect(status().isOk())
			.andExpect(jsonPath("$.cartId").value(nullValue()))
			.andExpect(jsonPath("$.items").isEmpty());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE status = 'active'", Integer.class)).isZero();
	}

	@Test
	void snapshotIsSingleUnlockedSelectWithoutStampOrCatalogCall() throws Exception {
		Cart cart = Cart.openFor(userId, clock);
		cart.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00"), "TRY", "Bir", null, clock);
		cart.addItem(UUID.randomUUID(), 1, new BigDecimal("20.00"), "TRY", "İki", null, clock);
		carts.saveAndFlush(cart);
		String cartBefore = updatedAt("carts");
		String itemsBefore = updatedAt("cart_items");
		clock.advance(Duration.ofMinutes(5));

		SqlCapture.start();
		snapshot(userId).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
		List<String> statements = SqlCapture.stop();

		assertThat(statements).hasSize(1);
		assertThat(statements.get(0).toLowerCase()).startsWith("select").contains("join").doesNotContain("for update");
		assertThat(updatedAt("carts")).isEqualTo(cartBefore);
		assertThat(updatedAt("cart_items")).isEqualTo(itemsBefore);
		assertThat(CATALOG.requests()).isEmpty();
	}

	@Test
	void missingWrongOrEmptyKeyIsUnauthorizedWithApiKeyChallenge() throws Exception {
		for (MockHttpServletRequestBuilder request : List.of(snapshotRequest(userId),
				snapshotRequest(userId).header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.randomKey()),
				snapshotRequest(userId).header(InternalApiKeyAuthenticationFilter.HEADER, ""),
				snapshotRequest(userId).header(InternalApiKeyAuthenticationFilter.HEADER,
						InternalTestKeys.ORDER_SERVICE_KEY_SHA256))) {
			String body = mockMvc.perform(request).andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, InternalApiKeyAuthenticationEntryPoint.CHALLENGE))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.instance").value(SNAPSHOT))
				.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain(userId.toString());
		}
	}

	@Test
	void userJwtIsNotAcceptedOnInternalChain() throws Exception {
		mockMvc.perform(snapshotRequest(userId).with(bearer(TestJwt.user(userId.toString()))))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, InternalApiKeyAuthenticationEntryPoint.CHALLENGE));
	}

	@Test
	void internalKeyIsNotAcceptedOnUserChain() throws Exception {
		mockMvc.perform(get("/api/cart").header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.MISSING_TOKEN));
	}

	/** Anahtar kontrolü yönlendirmeden önce: anahtarsız GET 401 (yöntem bilgisi bile sızmaz), anahtarlı GET 405. */
	@Test
	void getIsUnauthorizedWithoutKeyAndMethodNotAllowedWithKey() throws Exception {
		mockMvc.perform(get(SNAPSHOT))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, InternalApiKeyAuthenticationEntryPoint.CHALLENGE))
			.andExpect(header().doesNotExist(HttpHeaders.ALLOW));
		mockMvc.perform(get(SNAPSHOT).header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "{\"userId\":", "{\"userId\":\"kullanici-degil\"}", "{\"userId\":12345}",
			"[\"%s\"]", "{\"userId\":\"%s\"" })
	void unreadableBodyIsMalformedRequestWithoutEchoingValue(String template) throws Exception {
		String requestBody = template.formatted(userId);
		String body = withKey(post(SNAPSHOT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(userId.toString()).doesNotContain("kullanici-degil").doesNotContain("12345");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"userId\":null}", "{\"kullanici\":\"%s\"}" })
	void missingUserIdIsValidationError(String template) throws Exception {
		String body = withKey(post(SNAPSHOT).contentType(MediaType.APPLICATION_JSON).content(template.formatted(userId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("userId"))
			.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(userId.toString());
	}

	@Test
	void logsContainNoUserIdKeyHashOrToken(CapturedOutput output) throws Exception {
		Cart cart = Cart.openFor(userId, clock);
		cart.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00"), "TRY", "Log", null, clock);
		carts.saveAndFlush(cart);
		String wrongKey = InternalTestKeys.randomKey();
		String token = TestJwt.user(userId.toString());

		snapshot(userId).andExpect(status().isOk());
		mockMvc.perform(snapshotRequest(userId).header(InternalApiKeyAuthenticationFilter.HEADER, wrongKey))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(snapshotRequest(userId).with(bearer(token))).andExpect(status().isUnauthorized());
		withKey(post(SNAPSHOT).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + userId + "\""))
			.andExpect(status().isBadRequest());
		withKey(post(SNAPSHOT).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

		for (String secret : List.of(userId.toString(), InternalTestKeys.ORDER_SERVICE_KEY,
				InternalTestKeys.ORDER_SERVICE_KEY_SHA256, wrongKey, token)) {
			assertThat(output).doesNotContain(secret);
		}
		assertThat(output).contains("Internal request POST /internal/cart/snapshot client=order-service")
			.contains("Rejected internal request POST /internal/cart/snapshot -> UNAUTHORIZED")
			.contains("POST /internal/cart/snapshot -> MALFORMED_REQUEST")
			.contains("POST /internal/cart/snapshot -> VALIDATION_FAILED")
			.doesNotContain(" ERROR ");
	}

	// --- yardımcılar ---

	private ResultActions snapshot(UUID user) throws Exception {
		return mockMvc.perform(snapshotRequest(user).header(InternalApiKeyAuthenticationFilter.HEADER,
				InternalTestKeys.ORDER_SERVICE_KEY));
	}

	private static MockHttpServletRequestBuilder snapshotRequest(UUID user) {
		return post(SNAPSHOT).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + user + "\"}");
	}

	private ResultActions withKey(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(InternalApiKeyAuthenticationFilter.HEADER, InternalTestKeys.ORDER_SERVICE_KEY));
	}

	private String cartId(UUID user) {
		return jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM carts WHERE status = 'active' AND user_id = UUID_TO_BIN(?)",
				String.class, user.toString());
	}

	private String updatedAt(String table) {
		return String.join(",", jdbc.queryForList(
				"SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') FROM " + table + " ORDER BY updated_at", String.class));
	}

}
