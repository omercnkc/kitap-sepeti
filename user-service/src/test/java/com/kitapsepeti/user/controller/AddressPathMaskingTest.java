package com.kitapsepeti.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kitapsepeti.user.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/**
 * Adres id'si hata yanıtının {@code instance}'ında ve loglarda {@code :addressId} olur (common
 * {@code RequestPathMasker}, desen {@code SecurityConfig}'te); durum kodu ve gövdenin geri kalanı değişmez.
 */
@ExtendWith(OutputCaptureExtension.class)
class AddressPathMaskingTest extends ApiTestSupport {

	private static final String MASKED = "/api/me/addresses/:addressId";

	@Test
	void unknownAddressReturns404WithMaskedInstanceAndNoIdInLogs(CapturedOutput output) throws Exception {
		String token = registerAndGetAccessToken("maske@kitapsepeti.com");
		UUID addressId = UUID.randomUUID();

		mockMvc.perform(get("/api/me/addresses/" + addressId).with(bearer(token)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value(MASKED));
		mockMvc.perform(patch("/api/me/addresses/" + addressId).with(bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"city\":\"Ankara\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value(MASKED));
		mockMvc.perform(delete("/api/me/addresses/" + addressId.toString().toUpperCase()).with(bearer(token)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value(MASKED));
		// Kimliksiz istek (security entry point) de aynı maskeyi kullanır.
		mockMvc.perform(get("/api/me/addresses/" + addressId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.instance").value(MASKED));

		assertThat(output.getAll()).contains("GET " + MASKED + " -> RESOURCE_NOT_FOUND")
			.contains("DELETE " + MASKED + " -> RESOURCE_NOT_FOUND")
			.doesNotContainIgnoringCase(addressId.toString());
	}

	@Test
	void malformedIdIsMaskedToo(CapturedOutput output) throws Exception {
		String token = registerAndGetAccessToken("bozuk-id@kitapsepeti.com");

		mockMvc.perform(get("/api/me/addresses/bozuk-adres-4471").with(bearer(token)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.instance").value(MASKED));

		assertThat(output.getAll()).doesNotContain("bozuk-adres-4471");
	}

}
