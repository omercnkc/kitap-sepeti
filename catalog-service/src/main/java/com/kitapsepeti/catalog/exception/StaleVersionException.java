package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/**
 * İstemcinin gönderdiği versiyon kaydın güncel versiyonu değil (409 CONCURRENT_MODIFICATION); hiçbir şey yazılmaz.
 * İstek içindeki yarışları ayrıca Hibernate'in {@code @Version} kontrolü yakalar.
 */
public class StaleVersionException extends ApiException {

	public StaleVersionException() {
		super(CatalogErrorCode.CONCURRENT_MODIFICATION);
	}

}
