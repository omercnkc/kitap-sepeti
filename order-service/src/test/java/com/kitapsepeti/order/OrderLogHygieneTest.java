package com.kitapsepeti.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.kitapsepeti.order.controller.CheckoutTestSupport;
import com.kitapsepeti.order.messaging.PaymentResultListener;
import com.kitapsepeti.order.messaging.PoisonMessageException;
import com.kitapsepeti.order.service.PendingReconciliationJob;
import com.kitapsepeti.order.service.StockSyncJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * Genel log hijyeni testi: Tek testte mutlu checkout, kayıt sonrası hata, PaymentSucceeded tüketimi,
 * poison mesaj işleme, StockSyncJob ve PendingReconciliationJob turları çalıştırılır.
 * Yakalanan loglarda UUID kalıbı, tutar kalıbı (\d+\.\d{2}), adres test değerleri, "Bearer " ve
 * internal API anahtarı bulunmadığı doğrulanır.
 */
@TestPropertySource(properties = {
		"app.payment-results.enabled=true",
		"app.stock-sync.enabled=true",
		"app.pending-reconcile.enabled=true"
})
@ExtendWith(OutputCaptureExtension.class)
class OrderLogHygieneTest extends CheckoutTestSupport {

	private static final Pattern UUID_PATTERN = Pattern
		.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?<![0-9])\\d+\\.\\d{2}(?![0-9])");

	@Autowired
	private PaymentResultListener paymentResultListener;

	@Autowired
	private StockSyncJob stockSyncJob;

	@Autowired
	private PendingReconciliationJob pendingReconciliationJob;

	@Value("${app.clients.internal-api-key:}")
	private String internalApiKey;

	@Test
	void capturedLogsContainNoIdentifiersAmountsSecretsOrPersonalData(CapturedOutput output) throws Exception {
		int start = output.toString().length();

		// 1. Mutlu checkout
		Book book = Book.of("Mutlu Kitap", "149.90");
		stubCart(new Line(book, 2));
		stubLookup(book);
		stubReserveHeld();
		UUID paymentId = UUID.randomUUID();
		stubPaymentInitiated(paymentId, "initiated");

		UUID happyOrderId = idOf(checkout().andExpect(status().isCreated()));

		// 2. Bir kayıt sonrası hata (stok rezervasyonunda 409 yetersiz stok)
		resetRemotesAndUser();
		Book book2 = Book.of("Stoksuz Kitap", "99.50");
		stubCart(new Line(book2, 1));
		stubLookup(book2);
		stubReserve(stockProblem("INSUFFICIENT_STOCK", book2.id()));
		checkout().andExpect(status().isConflict());

		// 3. Bir PaymentSucceeded mesajı (mutlu sipariş için)
		String succeededJson = """
				{"eventId":"01920000-0000-7000-8000-00000000e001","eventVersion":1,"paymentId":"%s","orderId":"%s",\
				"amount":"299.80","currency":"TRY","occurredAt":"2026-10-05T10:00:00Z"}"""
			.formatted(paymentId, happyOrderId);
		Message succeededMessage = MessageBuilder.withBody(succeededJson.getBytes(StandardCharsets.UTF_8))
			.setType("PaymentSucceeded")
			.build();
		this.paymentResultListener.onMessage(succeededMessage);

		// 4. Bir poison mesaj
		Message poisonMessage = MessageBuilder.withBody("malformed-non-json".getBytes(StandardCharsets.UTF_8))
			.setType("PaymentSucceeded")
			.build();
		try {
			this.paymentResultListener.onMessage(poisonMessage);
		}
		catch (PoisonMessageException ignored) {
			// Listener veya container tarafından işlenir/yakalanır
		}

		// 5. Birer StockSyncJob ve uzlaştırma turu
		this.stockSyncJob.executeRound();
		this.pendingReconciliationJob.executeRound();

		String log = output.toString().substring(start);

		// Doğrulamalar:
		// 1. UUID kalıbı yok
		assertThat(UUID_PATTERN.matcher(log).find()).as("Logda hiçbir UUID kalıbı geçmemeli").isFalse();

		// 2. Tutar kalıbı yok (\d+\.\d{2})
		assertThat(AMOUNT_PATTERN.matcher(log).find()).as("Logda hiçbir tutar kalıbı geçmemeli").isFalse();

		// 3. Adres alanlarındaki test değerleri yok
		List<String> addressValues = List.of(RECIPIENT, PHONE, LINE1, LINE2, DISTRICT, CITY, POSTAL_CODE);
		for (String addr : addressValues) {
			assertThat(log).as("Adres test değeri '%s' logda geçmemeli", addr).doesNotContain(addr);
		}

		// 4. "Bearer " yok
		assertThat(log).as("'Bearer ' logda geçmemeli").doesNotContain("Bearer ");

		// 5. Internal API anahtarı yok
		if (this.internalApiKey != null && !this.internalApiKey.isBlank()) {
			assertThat(log).as("Internal API anahtarı logda geçmemeli").doesNotContain(this.internalApiKey);
		}
	}

}
