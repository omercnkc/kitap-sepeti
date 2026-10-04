package com.kitapsepeti.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kitapsepeti.order.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/orders/{orderId}}: yalnızca sahibi görür; başkasının ya da olmayan sipariş aynı 404 (varlık
 * sızdırılmaz). Yanıtta stok durumu ve ödeme id'si yok; hata {@code instance}'ı maskeli.
 */
class OrderQueryTest extends CheckoutTestSupport {

	private static final String MASKED = "/api/orders/:orderId";

	@Test
	void ownerSeesTheOrderWithTheCheckoutFields() throws Exception {
		Book book = stubHappyPath();
		ResultActions placed = checkout().andExpect(status().isCreated());
		UUID orderId = idOf(placed);
		String checkoutBody = placed.andReturn().getResponse().getContentAsString();

		String body = getOrder(orderId, this.token).andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(orderId.toString()))
			.andExpect(jsonPath("$.status").value("pending"))
			.andExpect(jsonPath("$.failureCode").isEmpty())
			.andExpect(jsonPath("$.currency").value("TRY"))
			.andExpect(jsonPath("$.items[0].bookId").value(book.id().toString()))
			.andExpect(jsonPath("$.items[0].title").value(book.title()))
			.andExpect(jsonPath("$.items[0].quantity").value(1))
			.andExpect(jsonPath("$.address.recipientName").value(RECIPIENT))
			.andExpect(jsonPath("$.address.country").value("TR"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty())
			.andExpect(jsonPath("$.stockState").doesNotExist())
			.andExpect(jsonPath("$.paymentId").doesNotExist())
			.andExpect(jsonPath("$.userId").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).contains("\"subtotal\":149.90", "\"discountAmount\":0.00", "\"totalAmount\":149.90",
				"\"unitPrice\":149.90", "\"lineTotal\":149.90");
		assertThat(body).isEqualTo(checkoutBody);
	}

	@Test
	void failedOrderShowsItsFailureCode() throws Exception {
		Book book = stubHappyPath();
		stubReserve(stockProblem("INSUFFICIENT_STOCK", book.id()));
		UUID orderId = orderIdOf(checkout().andExpect(status().isConflict()));

		getOrder(orderId, this.token).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("failed"))
			.andExpect(jsonPath("$.failureCode").value("OUT_OF_STOCK"));
	}

	@Test
	void anotherUsersOrderIsNotFound() throws Exception {
		stubHappyPath();
		UUID orderId = idOf(checkout().andExpect(status().isCreated()));
		String stranger = TestJwt.user(UUID.randomUUID().toString());

		String body = expectNotFound(getOrder(orderId, stranger));

		assertThat(body).doesNotContain(orderId.toString());
	}

	@Test
	void missingOrderIsNotFound() throws Exception {
		UUID missing = UUID.randomUUID();

		String body = expectNotFound(getOrder(missing, this.token));

		assertThat(body).doesNotContain(missing.toString());
	}

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/orders/{orderId}", UUID.randomUUID()).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value(MASKED));
	}

	/** Diğer servislerle aynı: geçersiz UUID path değişkeni 400 MALFORMED_REQUEST, değer yanıtta yok. */
	@Test
	void invalidOrderIdIsMalformedRequest() throws Exception {
		String body = mockMvc
			.perform(get("/api/orders/{orderId}", "siparis-degil-xq").with(bearer(this.token))
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
			.andExpect(jsonPath("$.instance").value(MASKED))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain("siparis-degil-xq");
	}

	private ResultActions getOrder(UUID orderId, String bearerToken) throws Exception {
		return mockMvc
			.perform(get("/api/orders/{orderId}", orderId).with(bearer(bearerToken)).accept(MediaType.APPLICATION_JSON));
	}

	private static String expectNotFound(ResultActions result) throws Exception {
		return result.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value(MASKED))
			.andExpect(jsonPath("$.orderId").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

}
