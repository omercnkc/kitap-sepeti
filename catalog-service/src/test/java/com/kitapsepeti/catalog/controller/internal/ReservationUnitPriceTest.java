package com.kitapsepeti.catalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** {@code unitPrice}, {@code priceAmount} ile aynı biçimde JSON sayısı (aynı değer, aynı ondalık). */
class ReservationUnitPriceTest extends InternalStockTestSupport {

	private static final Pattern UNIT_PRICE = Pattern.compile("\"unitPrice\":([^,}]+)");

	private static final Pattern PRICE_AMOUNT = Pattern.compile("\"priceAmount\":([^,}]+)");

	@Test
	void createReplayAndGetReturnUnitPriceAsNumberMatchingPriceAmount() throws Exception {
		UUID a = book("published", 5, new BigDecimal("149.90"));
		UUID b = book("published", 5, new BigDecimal("130"));
		UUID c = book("published", 5, new BigDecimal("89.5"));
		UUID orderId = UUID.randomUUID();
		Map<UUID, String> priceAmounts = new LinkedHashMap<>();
		for (UUID bookId : List.of(a, b, c)) {
			priceAmounts.put(bookId, priceAmountToken(bookId));
		}
		assertThat(priceAmounts.values()).containsExactlyInAnyOrder("149.90", "130.00", "89.50");

		Object body = request(orderId, a, 1, b, 2, c, 3);
		assertUnitPricesMatch(reserve(body).andExpect(status().isCreated()), priceAmounts);
		assertUnitPricesMatch(reserve(body).andExpect(status().isOk()), priceAmounts);
		assertUnitPricesMatch(fetch(orderId).andExpect(status().isOk()), priceAmounts);
	}

	private void assertUnitPricesMatch(ResultActions result, Map<UUID, String> priceAmounts) throws Exception {
		String json = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		JsonNode items = jsonMapper.readTree(json).get("items");
		List<String> tokens = tokens(UNIT_PRICE, json);
		assertThat(tokens).hasSize(priceAmounts.size());

		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			UUID bookId = UUID.fromString(item.get("bookId").asString());
			assertThat(item.get("unitPrice").isNumber()).as("unitPrice JSON sayısı: %s", json).isTrue();
			assertThat(tokens.get(i)).as("unitPrice ham değeri, kitap %s", bookId).isEqualTo(priceAmounts.get(bookId));
		}
	}

	/** Public detaydaki {@code priceAmount}'un yanıttaki ham yazımı (ör. {@code 130.00}). */
	private String priceAmountToken(UUID bookId) throws Exception {
		String json = mockMvc.perform(get("/api/books/{id}", bookId))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(jsonMapper.readTree(json).get("priceAmount").isNumber()).isTrue();
		return tokens(PRICE_AMOUNT, json).getFirst();
	}

	private static List<String> tokens(Pattern pattern, String json) {
		List<String> tokens = new ArrayList<>();
		Matcher matcher = pattern.matcher(json);
		while (matcher.find()) {
			tokens.add(matcher.group(1));
		}
		return tokens;
	}

}
