package com.kitapsepeti.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Stoğa eklenecek (pozitif) veya stoktan düşülecek (negatif) miktar; 0 olamaz (serviste kontrol edilir). */
public record StockAdjustmentRequest(
		@Schema(description = "Pozitif ekler, negatif düşer; 0 → 400 `VALIDATION_FAILED`. Stok rezerve miktarın "
				+ "altına inerse 409 `STOCK_BELOW_RESERVED`.")
		@NotNull @Min(-100_000) @Max(100_000) Integer delta) {
}
