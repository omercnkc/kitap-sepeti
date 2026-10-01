package com.kitapsepeti.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuthorResponse(UUID id, String name, String slug, Instant createdAt, Instant updatedAt) {

}
