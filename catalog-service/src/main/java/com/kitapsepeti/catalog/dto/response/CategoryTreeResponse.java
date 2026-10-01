package com.kitapsepeti.catalog.dto.response;

import java.util.List;
import java.util.UUID;

/** Kategori ağacında bir düğüm; {@code children} ada göre sıralı, yaprakta boş liste. */
public record CategoryTreeResponse(UUID id, String name, String slug, List<CategoryTreeResponse> children) {
}
