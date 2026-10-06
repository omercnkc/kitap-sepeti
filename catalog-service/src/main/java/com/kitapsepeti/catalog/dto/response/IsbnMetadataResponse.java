package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dış kaynaktan (Open Library) gelen ham kitap metadata'sı. Yazar/yayınevi/konu isimleri bizim DB
 * id'leri değil; create/update sırasında yazar adları sunucuda find-or-create edilir, kategoriler
 * admin formunda subject eşlemesiyle seçilir.
 */
public record IsbnMetadataResponse(
		@Schema(requiredMode = REQUIRED, description = "Normalize edilmiş ISBN-10/13.") String isbn,
		String title,
		String description,
		String coverUrl,
		Integer pageCount,
		@Schema(requiredMode = REQUIRED, description = "Yazar adları (sıra korunur).") List<String> authors,
		@Schema(requiredMode = REQUIRED, description = "Yayınevi adları.") List<String> publishers,
		@Schema(requiredMode = REQUIRED, description = "OL subject / konu adları (kategori eşlemesi için).") List<String> subjects) {

	public IsbnMetadataResponse {
		authors = (authors != null) ? List.copyOf(authors) : List.of();
		publishers = (publishers != null) ? List.copyOf(publishers) : List.of();
		subjects = (subjects != null) ? List.copyOf(subjects) : List.of();
	}

}
