package com.kitapsepeti.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kitapsepeti.order.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/**
 * Checkout başına tek INFO özet satırı (sonuç kodu + süre). Mutlu ve hatalı yollarda hiçbir log satırında sipariş,
 * kullanıcı, sepet, ödeme ya da kitap id'si, tutar, adres alanı veya kitap başlığı yok.
 */
@ExtendWith(OutputCaptureExtension.class)
class CheckoutLoggingTest extends CheckoutTestSupport {

	private static final Pattern SUMMARY = Pattern.compile("Checkout -> ([A-Z_]+) \\(durationMs=\\d+\\)");

	private final Book book = Book.of("Gizlibaslikxq Kitabi", "4321.87");

	private final UUID paymentId = UUID.randomUUID();

	private final List<String> secrets = new ArrayList<>();

	@Test
	void happyPathAndReadLogOnlyTheSummary(CapturedOutput output) throws Exception {
		UUID cartId = stubCart(new Line(this.book, 3));
		stubLookup(this.book);
		stubReserveHeld();
		stubPaymentInitiated(this.paymentId, "initiated");

		UUID orderId = idOf(checkout().andExpect(status().isCreated()));
		mockMvc.perform(get("/api/orders/{orderId}", orderId).with(bearer(this.token)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
		mockMvc
			.perform(get("/api/orders/{orderId}", orderId).with(bearer(TestJwt.user(UUID.randomUUID().toString())))
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound());

		assertThat(summaries(output)).containsExactly("ORDER_PLACED");
		assertClean(output, orderId, cartId);
	}

	@Test
	void failurePathsLogOnlyTheSummaryAndCodes(CapturedOutput output) throws Exception {
		UUID cartId = stubCart(new Line(this.book, 3));
		stubLookup(this.book);
		stubReserve(stockProblem("INSUFFICIENT_STOCK", this.book.id()));
		UUID outOfStock = orderIdOf(checkout().andExpect(status().isConflict()));

		stubReserve(problem(400, "VALIDATION_FAILED"));
		UUID rejected = orderIdOf(checkout().andExpect(status().isServiceUnavailable()));

		stubReserveHeld();
		stubPayment(problem(409, "PAYMENT_ORDER_MISMATCH"));
		UUID paymentRejected = orderIdOf(checkout().andExpect(status().isServiceUnavailable()));

		stubPayment(problem(503, "PAYMENT_PROVIDER_UNAVAILABLE"));
		UUID unknown = idOf(checkout().andExpect(status().isCreated()));

		checkout().andExpect(status().isConflict());

		this.secrets.add(this.userId.toString());
		resetRemotesAndUser();
		stubCart(new Line(this.book, 3));
		stubLookup(this.book.outOfStock());
		checkout().andExpect(status().isConflict());

		checkout(this.token, CHECKOUT_BODY.replace("\"country\":\"TR\"", "\"country\":\"tr\""))
			.andExpect(status().isBadRequest());

		assertThat(summaries(output)).containsExactly("INSUFFICIENT_STOCK", "CATALOG_UNAVAILABLE",
				"PAYMENT_UNAVAILABLE", "ORDER_PLACED", "ORDER_PENDING_EXISTS", "BOOK_NOT_AVAILABLE");
		assertThat(output).contains("Checkout reserve rejected by catalog (status=400, code=VALIDATION_FAILED)")
			.contains("Checkout payment rejected by payment service (status=409, code=PAYMENT_ORDER_MISMATCH)")
			.contains("Checkout payment outcome is unknown; order stays pending with stock held");
		assertClean(output, outOfStock, rejected, paymentRejected, unknown, cartId);
	}

	private static List<String> summaries(CapturedOutput output) {
		List<String> outcomes = new ArrayList<>();
		Matcher matcher = SUMMARY.matcher(output.getAll());
		while (matcher.find()) {
			outcomes.add(matcher.group(1));
		}
		return outcomes;
	}

	private void assertClean(CapturedOutput output, UUID... ids) {
		this.secrets.addAll(List.of(this.userId.toString(), this.paymentId.toString(), this.book.id().toString(),
				this.book.title(), "Gizlibaslikxq", "4321.87", "12965.61", "12965", RECIPIENT, PHONE, "5559876543",
				LINE1, LINE2, DISTRICT, CITY, POSTAL_CODE, this.token));
		for (UUID id : ids) {
			this.secrets.add(id.toString());
		}
		for (String secret : this.secrets) {
			assertThat(output.getAll()).as("log must not contain a sensitive value").doesNotContain(secret);
		}
	}

}
