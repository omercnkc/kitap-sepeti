package com.kitapsepeti.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tüketici sözleşme testi: Payment servisinin olay belgelerindeki ({@code docs/events/payment-succeeded.md} ve
 * {@code docs/events/payment-failed.md}) örnek JSON'lar Order'ın {@link PaymentResultMessageParser}'ı ile okunur.
 */
class PaymentEventsConsumerContractTest {

	private static final Path PAYMENT_SUCCEEDED_DOC = Path.of("..", "docs", "events", "payment-succeeded.md");

	private static final Path PAYMENT_FAILED_DOC = Path.of("..", "docs", "events", "payment-failed.md");

	private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n```");

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final PaymentResultMessageParser parser = new PaymentResultMessageParser(jsonMapper);

	@Test
	void paymentSucceededDocExampleIsReadableByOrderParser() throws Exception {
		assertThat(PAYMENT_SUCCEEDED_DOC).as("payment-succeeded belgesi").exists();
		String json = extractJsonExample(PAYMENT_SUCCEEDED_DOC);

		Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
			.setType(PaymentResultMessageParser.SUCCEEDED)
			.build();

		PaymentResultMessage result = this.parser.parse(message);
		assertThat(result).isInstanceOf(PaymentResultMessage.Succeeded.class);

		PaymentResultMessage.Succeeded succeeded = (PaymentResultMessage.Succeeded) result;
		JsonNode root = jsonMapper.readTree(json);

		assertThat(succeeded.eventId()).isEqualTo(UUID.fromString(root.get("eventId").asText()));
		assertThat(succeeded.paymentId()).isEqualTo(UUID.fromString(root.get("paymentId").asText()));
		assertThat(succeeded.orderId()).isEqualTo(UUID.fromString(root.get("orderId").asText()));
		assertThat(succeeded.amount()).isEqualByComparingTo(new BigDecimal(root.get("amount").asText()));
		assertThat(succeeded.currency()).isEqualTo(root.get("currency").asText());
		assertThat(succeeded.occurredAt()).isEqualTo(Instant.parse(root.get("occurredAt").asText()));
	}

	@Test
	void paymentFailedDocExampleIsReadableByOrderParser() throws Exception {
		assertThat(PAYMENT_FAILED_DOC).as("payment-failed belgesi").exists();
		String json = extractJsonExample(PAYMENT_FAILED_DOC);

		Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
			.setType(PaymentResultMessageParser.FAILED)
			.build();

		PaymentResultMessage result = this.parser.parse(message);
		assertThat(result).isInstanceOf(PaymentResultMessage.Failed.class);

		PaymentResultMessage.Failed failed = (PaymentResultMessage.Failed) result;
		JsonNode root = jsonMapper.readTree(json);

		assertThat(failed.eventId()).isEqualTo(UUID.fromString(root.get("eventId").asText()));
		assertThat(failed.paymentId()).isEqualTo(UUID.fromString(root.get("paymentId").asText()));
		assertThat(failed.orderId()).isEqualTo(UUID.fromString(root.get("orderId").asText()));
		assertThat(failed.amount()).isEqualByComparingTo(new BigDecimal(root.get("amount").asText()));
		assertThat(failed.currency()).isEqualTo(root.get("currency").asText());
		assertThat(failed.failureCode()).isEqualTo(root.get("failureCode").asText());
		assertThat(failed.occurredAt()).isEqualTo(Instant.parse(root.get("occurredAt").asText()));
	}

	private static String extractJsonExample(Path docPath) throws IOException {
		String content = Files.readString(docPath, StandardCharsets.UTF_8);
		Matcher matcher = JSON_BLOCK.matcher(content);
		if (!matcher.find()) {
			throw new IllegalArgumentException("No ```json block found in " + docPath);
		}
		return matcher.group(1).trim();
	}

}
