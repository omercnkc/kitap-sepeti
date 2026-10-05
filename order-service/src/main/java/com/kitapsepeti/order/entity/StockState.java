package com.kitapsepeti.order.entity;

/**
 * Siparişin Catalog'daki stok rezervasyonunun durumu. DB'de küçük harf ({@link StockStateConverter}).
 * Sipariş önce yazılır, Catalog çağrısı sonra yapılır: {@link #REQUESTED} ile başlar. {@link #COMMITTED} ve
 * {@link #RELEASED} son durumlardır.
 */
public enum StockState implements DbEnum {

	/** Rezervasyon istendi, Catalog yanıtı henüz işlenmedi. */
	REQUESTED("requested"),
	/** Catalog stoğu ayırdı. */
	HELD("held"),
	/** Ödeme sonrası ayrılan stok kesinleşti (yalnızca ödenmiş sipariş). */
	COMMITTED("committed"),
	/** Başarısız sipariş için ayrılan stok bırakıldı. */
	RELEASED("released"),
	/** Ödeme başarılı ancak rezervasyon süresi dolduğu için stok kaybedildi (yalnızca ödenmiş sipariş). */
	LOST("lost");

	private final String dbValue;

	StockState(String dbValue) {
		this.dbValue = dbValue;
	}

	@Override
	public String dbValue() {
		return dbValue;
	}

}
