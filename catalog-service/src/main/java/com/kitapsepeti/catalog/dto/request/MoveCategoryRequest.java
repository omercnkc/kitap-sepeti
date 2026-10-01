package com.kitapsepeti.catalog.dto.request;

import java.util.UUID;

/** Kategorinin yeni üst kategorisi; null (veya alanın hiç gönderilmemesi) kategoriyi kök yapar. */
public record MoveCategoryRequest(UUID parentId) {

}
