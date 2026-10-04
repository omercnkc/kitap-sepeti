package com.kitapsepeti.order.gateway;

import java.util.Objects;
import java.util.UUID;

/** Kitap ve adet: sepet görüntüsünün satırı ve stok rezervasyonunun kalemi. {@link #toString()} kimlik içermez. */
public record StockLine(UUID bookId, int quantity) {

	public StockLine {
		Objects.requireNonNull(bookId, "bookId");
	}

	@Override
	public String toString() {
		return "StockLine[redacted]";
	}

}
