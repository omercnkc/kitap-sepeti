package com.kitapsepeti.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.user.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AddressControllerTest extends ApiTestSupport {

	private static final String BASE = "/api/me/addresses";

	@Test
	void firstAddressBecomesDefaultEvenIfNotRequested() throws Exception {
		String token = registerAndGetAccessToken("ilk@kitapsepeti.com");

		String body = createAddress(token, addressJson("Ev", null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.isDefault").value(true))
			.andExpect(jsonPath("$.country").value("TR"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String id = JsonPath.read(body, "$.id");

		createAddress(token, addressJson("İş", false));
		mockMvc.perform(get(BASE + "/" + id).with(bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isDefault").value(true));
	}

	@Test
	void createReturnsLocationOfNewAddress() throws Exception {
		String token = registerAndGetAccessToken("konum@kitapsepeti.com");

		ResultActions result = createAddress(token, addressJson("Ev", null)).andExpect(status().isCreated());
		String id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");

		result.andExpect(header().string("Location", BASE + "/" + id));
	}

	@Test
	void newDefaultAddressReplacesOldOneWithoutUniqueViolation() throws Exception {
		String token = registerAndGetAccessToken("ikinci@kitapsepeti.com");
		String userId = userIdOf("ikinci@kitapsepeti.com");
		String first = createAndGetId(token, addressJson("Ev", null));

		String second = JsonPath.read(createAddress(token, addressJson("İş", true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.isDefault").value(true))
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

		mockMvc.perform(get(BASE).with(bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(second))
			.andExpect(jsonPath("$[0].isDefault").value(true))
			.andExpect(jsonPath("$[1].id").value(first))
			.andExpect(jsonPath("$[1].isDefault").value(false));
		assertThat(defaultCount(userId)).isEqualTo(1);
	}

	@Test
	void defaultAddressCannotBeUnsetButAnotherCanBecomeDefault() throws Exception {
		String token = registerAndGetAccessToken("varsayilan@kitapsepeti.com");
		String userId = userIdOf("varsayilan@kitapsepeti.com");
		String first = createAndGetId(token, addressJson("Ev", null));
		String second = createAndGetId(token, addressJson("İş", false));

		patchAddress(token, first, "{\"isDefault\":false}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEFAULT_ADDRESS_REQUIRED"));

		// Zaten varsayılan olana tekrar true: varsayılan kalmalı.
		patchAddress(token, first, "{\"isDefault\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isDefault").value(true));
		assertThat(isDefault(first)).isTrue();

		patchAddress(token, second, "{\"isDefault\":true,\"city\":\"Ankara\",\"line2\":\"\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isDefault").value(true))
			.andExpect(jsonPath("$.city").value("Ankara"))
			.andExpect(jsonPath("$.line2").doesNotExist())
			.andExpect(jsonPath("$.recipientName").value("Ali Veli"));
		assertThat(isDefault(first)).isFalse();
		assertThat(isDefault(second)).isTrue();
		assertThat(defaultCount(userId)).isEqualTo(1);
	}

	@Test
	void deletingDefaultPromotesNewestRemainingAddress() throws Exception {
		String token = registerAndGetAccessToken("silme@kitapsepeti.com");
		String first = createAndGetId(token, addressJson("Ev", null));
		String second = createAndGetId(token, addressJson("İş", false));
		String third = createAndGetId(token, addressJson("Yazlık", false));

		mockMvc.perform(delete(BASE + "/" + first).with(bearer(token))).andExpect(status().isNoContent());
		mockMvc.perform(get(BASE).with(bearer(token)))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(third))
			.andExpect(jsonPath("$[0].isDefault").value(true))
			.andExpect(jsonPath("$[1].id").value(second))
			.andExpect(jsonPath("$[1].isDefault").value(false));

		mockMvc.perform(delete(BASE + "/" + third).with(bearer(token))).andExpect(status().isNoContent());
		mockMvc.perform(get(BASE).with(bearer(token)))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(second))
			.andExpect(jsonPath("$[0].isDefault").value(true));

		mockMvc.perform(delete(BASE + "/" + second).with(bearer(token))).andExpect(status().isNoContent());
		mockMvc.perform(get(BASE).with(bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void otherUsersAddressIsIndistinguishableFromMissing() throws Exception {
		String tokenA = registerAndGetAccessToken("a@kitapsepeti.com");
		String tokenB = registerAndGetAccessToken("b@kitapsepeti.com");
		String addressOfB = createAndGetId(tokenB, addressJson("B Ev", null));
		Map<String, Object> before = addressRow(addressOfB);

		mockMvc.perform(get(BASE + "/" + addressOfB).with(bearer(tokenA)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		patchAddress(tokenA, addressOfB, "{\"city\":\"Hacklendi\"}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(delete(BASE + "/" + addressOfB).with(bearer(tokenA)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		assertThat(addressRow(addressOfB)).isEqualTo(before);
		mockMvc.perform(get(BASE).with(bearer(tokenA))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void invalidAddressBodyListsFieldErrors() throws Exception {
		String token = registerAndGetAccessToken("gecersiz@kitapsepeti.com");

		createAddress(token, """
				{"recipientName":"","phone":"5550000000","line1":"Cadde 1","city":"İstanbul","country":"tr"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("recipientName", "country")));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM addresses", Integer.class)).isZero();
	}

	@Test
	void invalidPhoneIsRejectedAndValidIsNormalized() throws Exception {
		String token = registerAndGetAccessToken("ceptel@kitapsepeti.com");

		createAddress(token, """
				{"recipientName":"Ali Veli","phone":"123","line1":"Cadde 1","city":"İstanbul","country":"TR"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", hasItem("phone")));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM addresses", Integer.class)).isZero();

		String id = createAndGetId(token, """
				{"recipientName":"Ali Veli","phone":"+905550000000","line1":"Cadde 1","city":"İstanbul","country":"TR"}
				""");

		assertThat(jdbc.queryForObject("SELECT phone FROM addresses WHERE id = UUID_TO_BIN(?)", String.class, id))
			.isEqualTo("5550000000");
	}

	private ResultActions createAddress(String token, String json) throws Exception {
		return mockMvc.perform(post(BASE).with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String createAndGetId(String token, String json) throws Exception {
		String body = createAddress(token, json).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(body, "$.id");
	}

	private ResultActions patchAddress(String token, String id, String json) throws Exception {
		return mockMvc.perform(patch(BASE + "/" + id).with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private int defaultCount(String userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM addresses WHERE user_id = UUID_TO_BIN(?) AND is_default",
				Integer.class, userId);
	}

	private boolean isDefault(String addressId) {
		return jdbc.queryForObject("SELECT is_default FROM addresses WHERE id = UUID_TO_BIN(?)", Boolean.class,
				addressId);
	}

	private Map<String, Object> addressRow(String addressId) {
		return jdbc.queryForMap("SELECT recipient_name, city, is_default, updated_at FROM addresses WHERE id = UUID_TO_BIN(?)",
				addressId);
	}

	private static String addressJson(String label, Boolean isDefault) {
		String defaultPart = (isDefault == null) ? "" : ",\"isDefault\":" + isDefault;
		return """
				{"label":"%s","recipientName":"Ali Veli","phone":"5550000000","line1":"Bağdat Cd. No:1",
				 "line2":"Daire 3","district":"Kadıköy","city":"İstanbul","postalCode":"34710"%s}
				""".formatted(label, defaultPart);
	}

}
