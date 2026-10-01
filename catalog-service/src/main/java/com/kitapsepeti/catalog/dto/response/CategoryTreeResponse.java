package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kategori ağacında bir düğüm; {@code children} ada göre sıralı, yaprakta boş liste. */
public record CategoryTreeResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String slug,
		@Schema(requiredMode = REQUIRED) List<CategoryTreeResponse> children) {
}
