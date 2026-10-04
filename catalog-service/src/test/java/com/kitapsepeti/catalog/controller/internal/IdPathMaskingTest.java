package com.kitapsepeti.catalog.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.kitapsepeti.catalog.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Yoldaki id'ler hata yanıtının {@code instance}'ında ve loglarda {@code :<ad>} olur (common
 * {@code RequestPathMasker}, desenler {@code SecurityConfig}'te); durum kodu ve gövdenin geri kalanı değişmez.
 */
@ExtendWith(OutputCaptureExtension.class)
class IdPathMaskingTest extends InternalStockTestSupport {

	@Test
	void unknownReservationReturns404WithMaskedInstance(CapturedOutput output) throws Exception {
		UUID orderId = UUID.randomUUID();

		fetch(orderId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value(BASE + "/:orderId"));
		commit(orderId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value(BASE + "/:orderId/commit"));
		release(orderId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value(BASE + "/:orderId/release"));

		assertThat(output.getAll()).contains("GET " + BASE + "/:orderId -> RESOURCE_NOT_FOUND")
			.contains("POST " + BASE + "/:orderId/commit -> RESOURCE_NOT_FOUND")
			.doesNotContainIgnoringCase(orderId.toString());
	}

	@Test
	void commitWithoutKeyReturns401WithMaskedInstance(CapturedOutput output) throws Exception {
		UUID orderId = UUID.randomUUID();

		mockMvc.perform(post(BASE + "/" + orderId + "/commit"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value(BASE + "/:orderId/commit"));
		// Büyük harfli UUID de yakalanır (desen eşleşmesi segment yapısına bakar).
		mockMvc.perform(post(BASE + "/" + orderId.toString().toUpperCase() + "/release"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.instance").value(BASE + "/:orderId/release"));

		assertThat(output.getAll()).contains("Rejected internal request POST " + BASE + "/:orderId/commit -> UNAUTHORIZED")
			.doesNotContainIgnoringCase(orderId.toString());
	}

	@Test
	void stateConflictReturns409WithMaskedInstance(CapturedOutput output) throws Exception {
		UUID book = book("published", 5);
		UUID orderId = UUID.randomUUID();
		reserve(request(orderId, book, 1)).andExpect(status().isCreated());
		release(orderId).andExpect(status().isOk());

		commit(orderId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_RELEASED"))
			.andExpect(jsonPath("$.instance").value(BASE + "/:orderId/commit"));

		assertThat(output.getAll()).contains("Internal request POST " + BASE + "/:orderId/commit client=order-service")
			.contains("POST " + BASE + "/:orderId/commit -> RESERVATION_RELEASED")
			.doesNotContainIgnoringCase(orderId.toString());
	}

	@Test
	void publicAndAdminIdPathsAreMasked(CapturedOutput output) throws Exception {
		UUID bookId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();

		mockMvc.perform(get("/api/books/" + bookId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value("/api/books/:bookId"));
		mockMvc.perform(get("/api/admin/books/" + bookId).with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value("/api/admin/books/:bookId"));
		mockMvc.perform(post("/api/admin/books/" + bookId + "/publish").with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value("/api/admin/books/:bookId/publish"));
		mockMvc.perform(get("/api/admin/categories/" + categoryId).with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value("/api/admin/categories/:categoryId"));
		// Yetkisiz istek de (security handler'ı) aynı maskeyi kullanır.
		mockMvc.perform(get("/api/admin/books/" + bookId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.instance").value("/api/admin/books/:bookId"));
		// Desen segment yapısına bakar: UUID olmayan id de maskelenir; sabit yol (lookup) maskelenmez.
		mockMvc.perform(get("/api/books/abc"))
			.andExpect(jsonPath("$.instance").value("/api/books/:bookId"));
		mockMvc.perform(get("/api/books/lookup"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.instance").value("/api/books/lookup"));
		// Desene uymayan yolda güvenlik ağı: UUID biçimli segment :id olur.
		UUID strayId = UUID.randomUUID();
		mockMvc.perform(get("/api/admin/olmayan/" + strayId + "/yol").with(bearer(TestJwt.admin(SUBJECT))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.instance").value("/api/admin/olmayan/:id/yol"));

		assertThat(output.getAll()).contains("GET /api/books/:bookId -> RESOURCE_NOT_FOUND")
			.contains("GET /api/admin/categories/:categoryId -> RESOURCE_NOT_FOUND")
			.doesNotContainIgnoringCase(bookId.toString())
			.doesNotContainIgnoringCase(categoryId.toString())
			.doesNotContainIgnoringCase(strayId.toString());
	}

}
