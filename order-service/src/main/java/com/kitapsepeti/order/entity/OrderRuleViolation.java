package com.kitapsepeti.order.entity;

import java.util.Objects;

/**
 * {@link Order#place} iş kuralı ihlali: istemcinin düzeltebileceği (ör. boş sepet, tamamı ücretsiz sepet) ya da
 * sepetteki verinin sipariş kuralına uymadığı durum. Mesaj yalnızca kodu taşır; satır değeri, tutar ya da adres yazılmaz.
 * Kod → HTTP eşlemesi checkout ucunda (Adım 4/5; ör. {@link Code#ORDER_TOTAL_ZERO} → 422).
 */
public class OrderRuleViolation extends RuntimeException {

	public enum Code {

		/** Satır yok. */
		EMPTY_ORDER,
		/** Aynı kitap iki satırda. */
		DUPLICATE_BOOK,
		/** Adet 1–99 dışında ({@code ck_order_items_quantity}). */
		INVALID_QUANTITY,
		/** Birim fiyat negatif ya da 2'den fazla ondalık taşıyor. */
		INVALID_PRICE,
		/** Para birimi 3 büyük harf değil. */
		INVALID_CURRENCY,
		/** Ara toplam 0 (tamamı ücretsiz sepet); sipariş yazılmaz ({@code ck_orders_subtotal}/{@code ck_orders_total}). */
		ORDER_TOTAL_ZERO

	}

	private final Code code;

	public OrderRuleViolation(Code code) {
		super("Order rule violated: " + Objects.requireNonNull(code, "code"));
		this.code = code;
	}

	public Code code() {
		return code;
	}

}
