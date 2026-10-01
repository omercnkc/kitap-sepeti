package com.kitapsepeti.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PublisherResponse(UUID id, String name, String slug, Instant createdAt, Instant updatedAt) {

}
