package com.kitapsepeti.catalog.dto.response;

import java.util.UUID;

/** Kitap yanıtlarında yazar özeti. */
public record AuthorRef(UUID id, String name, String slug) {
}
