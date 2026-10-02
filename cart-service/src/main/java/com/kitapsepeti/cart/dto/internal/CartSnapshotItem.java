package com.kitapsepeti.cart.dto.internal;

import java.math.BigDecimal;
import java.util.UUID;

/** @param unitPriceSnapshot sepete eklendiği (ya da en son yeniden eklendiği) andaki fiyat, scale 2, JSON sayı */
public record CartSnapshotItem(UUID bookId, int quantity, BigDecimal unitPriceSnapshot, String currency, String title) {
}
