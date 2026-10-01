package com.kitapsepeti.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Admin düz liste/detay; {@code parentId} null ise kök kategori. */
public record CategoryResponse(UUID id, UUID parentId, String name, String slug, Instant createdAt,
		Instant updatedAt) {

}
