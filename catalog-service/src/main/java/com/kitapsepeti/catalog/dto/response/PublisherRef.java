package com.kitapsepeti.catalog.dto.response;

import java.util.UUID;

/** Kitap yanıtlarında yayınevi özeti. */
public record PublisherRef(UUID id, String name, String slug) {
}
