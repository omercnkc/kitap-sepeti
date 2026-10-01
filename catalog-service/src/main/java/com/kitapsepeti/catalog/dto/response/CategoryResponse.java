package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Admin düz liste/detay; {@code parentId} null ise kök kategori. */
public record CategoryResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		UUID parentId,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String slug,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
