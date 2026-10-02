package com.kitapsepeti.cart.exception;

import com.kitapsepeti.common.error.ApiException;

/**
 * Catalog'a ulaşılamadı ya da beklenmeyen yanıt verdi (503). Log WARN seviyesinde tek satır, stack trace yok; nedenin
 * yalnızca sınıf adı loglanır, mesajı (URL/host içerebilir) ne yanıta ne loga yazılır.
 */
public class CatalogUnavailableException extends ApiException {

	public CatalogUnavailableException(Throwable cause) {
		super(CartErrorCode.CATALOG_UNAVAILABLE);
		initCause(cause);
	}

}
