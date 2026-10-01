package com.kitapsepeti.catalog.dto.response;

import java.util.UUID;

/** Kitap detayında kategori özeti. */
public record CategoryRef(UUID id, String name, String slug) {
}
