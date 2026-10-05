package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dış kaynaktan (Open Library) gelen ham kitap metadata'sı. Yazar/yayınevi isimleri bizim DB id'leri değil;
 * admin formu isim eşleştirmesi yapar.
 */
public record IsbnMetadataResponse(
		@Schema(requiredMode = REQUIRED, description = "Normalize edilmiş ISBN-10/13.") String isbn,
		String title,
		String description,
		String coverUrl,
		Integer pageCount,
		@Schema(requiredMode = REQUIRED, description = "Yazar adları (sıra korunur).") List<String> authors,
		@Schema(requiredMode = REQUIRED, description = "Yayınevi adları.") List<String> publishers) {
}
