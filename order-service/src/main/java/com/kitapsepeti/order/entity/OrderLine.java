package com.kitapsepeti.order.entity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@link Order#place} girdisi: checkout anındaki sepet satırı (kitap, başlık, adet, birim fiyat). Doğrulama
 * {@link Order#place}'te; bu kayıt yalnızca taşıyıcıdır. {@link #toString()} kimlik ve tutar içermez.
 */
public record OrderLine(UUID bookId, String title, int quantity, BigDecimal unitPrice) {

	@Override
	public String toString() {
		return "OrderLine[redacted]";
	}

}
