package com.kitapsepeti.catalog.dto.request;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sipariş için stok ayırma isteği (internal). Aynı {@code bookId} iki kez geçemez; bu kontrol serviste yapılır
 * (alan {@code items}).
 */
public record ReserveStockRequest(
		@NotNull UUID orderId,
		@Schema(description = "Aynı `bookId` iki kez geçemez (400 `VALIDATION_FAILED`, alan `items`)")
		@NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Item> items) {

	@Schema(name = "ReserveStockItem")
	public record Item(@NotNull UUID bookId, @NotNull @Min(1) @Max(100) Integer quantity) {
	}

}
