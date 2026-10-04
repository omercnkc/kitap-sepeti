package com.kitapsepeti.order.entity;

/**
 * Durum geçmişi {@code reason} ve sipariş {@code failure_code} makine kodları ({@code ^[A-Z][A-Z0-9_]*$}, en fazla 64).
 * Başarısız siparişte geçmiş satırının reason'ı failure code'un kendisidir. Kodlar API ve olay sözleşmesinin parçası
 * olacak; değiştirmek kırıcı değişikliktir.
 */
public final class OrderReasons {

	/** İlk geçmiş satırı: null → pending. */
	public static final String ORDER_PLACED = "ORDER_PLACED";

	/** pending → paid. */
	public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";

	// Failure code'lar (pending → failed). Kullanımları sonraki adımlarda.

	/** Catalog rezervasyonu stok yetersizliğiyle reddetti. */
	public static final String OUT_OF_STOCK = "OUT_OF_STOCK";

	/** Catalog rezervasyonu kitap yok / yayında değil diye reddetti (lookup ile rezervasyon arasında değişti). */
	public static final String BOOK_NOT_AVAILABLE = "BOOK_NOT_AVAILABLE";

	/** Catalog'a ulaşılamadı / yanıt vermedi (circuit breaker açık dahil). */
	public static final String CATALOG_UNAVAILABLE = "CATALOG_UNAVAILABLE";

	/** Payment'a ulaşılamadı / yanıt vermedi (circuit breaker açık dahil). */
	public static final String PAYMENT_UNAVAILABLE = "PAYMENT_UNAVAILABLE";

	/** Payment ödeme başlatma isteğini 4xx ile reddetti (bizim taraftaki bir hata: anahtar, gövde, eşleşme). */
	public static final String PAYMENT_REJECTED = "PAYMENT_REJECTED";

	/** Sağlayıcı kartı reddetti (payment-service'in failure code'u). */
	public static final String CARD_DECLINED = "CARD_DECLINED";

	/** Ödeme başka bir nedenle başarısız. */
	public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

	/** Ödeme süresi içinde sonuçlanmadı (zaman aşımı görevi). */
	public static final String ORDER_EXPIRED = "ORDER_EXPIRED";

	private OrderReasons() {
	}

}
