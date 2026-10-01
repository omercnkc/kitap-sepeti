package com.kitapsepeti.catalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Stoğa eklenecek (pozitif) veya stoktan düşülecek (negatif) miktar; 0 olamaz (serviste kontrol edilir). */
public record StockAdjustmentRequest(@NotNull @Min(-100_000) @Max(100_000) Integer delta) {
}
