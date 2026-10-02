package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Toplu kitap okuması. Yalnızca yayındaki kitaplar, istekteki ilk geçiş sırasıyla; bulunamayanlar listede yok. */
public record BookLookupResponse(
		@Schema(requiredMode = REQUIRED, description = "Bulunan yayındaki kitaplar; istekteki sırayla, her id en fazla bir kez.")
		List<BookSummaryResponse> items) {
}
