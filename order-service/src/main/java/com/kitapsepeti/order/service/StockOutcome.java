package com.kitapsepeti.order.service;

/** Stok senkronizasyon ve dispatch işlemi sonucu. */
public enum StockOutcome {

	/** Stok başarıyla kesinleştirildi (committed). */
	COMMITTED,
	/** Stok süresi dolduğu veya rezervasyon bulunamadığı için kaybedildi (lost). */
	LOST,
	/** Stok başarıyla serbest bırakıldı (released). */
	RELEASED,
	/** Stok işlemi tamamlanamadı ve held olarak kaldı (tekrar denenecek). */
	HELD,
	/** Sipariş durumu zaten değişmişti; kilitli okuma no-op oldu. */
	NO_OP,
	/** Hata oluştu (yapılandırma veya beklenmeyen durum). */
	ERROR,
	/** Circuit breaker açık veya bağlantı kurulamadı (NotPerformed). */
	NOT_PERFORMED

}
