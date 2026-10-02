package com.kitapsepeti.catalog.dto.request;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code GET /api/books/lookup} query parametresi. {@code ids} hem virgülle ({@code ids=a,b}) hem tekrarla
 * ({@code ids=a&ids=b}) verilebilir. Limit tekrarlı id'leri de sayar. Eksik/boş liste, limit aşımı ve bozuk UUID
 * 400 VALIDATION_FAILED olur (alan {@code ids}; gönderilen değerler yanıtta yer almaz).
 */
public record BookLookupRequest(
		@Parameter(description = "Kitap id'leri; virgülle ayrılmış ya da tekrarlı parametre. Tekrarlı id tek kez döner.")
		@NotEmpty @Size(min = 1, max = BookLookupRequest.MAX_IDS) List<@NotNull UUID> ids) {

	public static final int MAX_IDS = 50;

}
