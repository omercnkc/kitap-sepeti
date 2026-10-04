package com.kitapsepeti.order.gateway;

/** {@link CatalogGateway#commit} sonucu. 404 {@code RESOURCE_NOT_FOUND} (rezervasyon yok) {@link Rejected} olarak döner. */
public sealed interface CommitResult permits CommitResult.Committed, CommitResult.AlreadyReleased, Rejected,
		NotPerformed, Unknown {

	/** Stok düşüldü; zaten {@code committed} ise de (idempotent tekrar) bu. */
	record Committed() implements CommitResult {
	}

	/**
	 * 409 {@code RESERVATION_RELEASED}: rezervasyon iptal edilmiş ya da süresi dolup serbest bırakılmış; stok bu sipariş
	 * için tutulmuyor. Ödenmiş siparişte telafi = iade (catalog-internal-stock.md "Süre dolumu").
	 */
	record AlreadyReleased() implements CommitResult {
	}

}
