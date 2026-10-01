package com.kitapsepeti.catalog.exception;

/**
 * İstemcinin gönderdiği versiyon kaydın güncel versiyonu değil (409 CONCURRENT_MODIFICATION); hiçbir şey yazılmaz.
 * İstek içindeki yarışları ayrıca Hibernate'in {@code @Version} kontrolü yakalar.
 */
public class StaleVersionException extends ApiException {

	public StaleVersionException() {
		super(ErrorCode.CONCURRENT_MODIFICATION);
	}

}
