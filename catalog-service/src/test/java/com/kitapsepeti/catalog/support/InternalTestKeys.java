package com.kitapsepeti.catalog.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Yalnızca testte geçerli servis anahtarı; SHA-256 özeti application-test.yml'de. Gerçek anahtarla (.env) ilgisi
 * yoktur ve src/test dışına çıkmamalıdır.
 */
public final class InternalTestKeys {

	public static final String HEADER = "X-Internal-Api-Key";

	public static final String ORDER_SERVICE_KEY = "test-only-order-service-key-f5277e0cab624ab0b0a51ad4387a1efa";

	public static final String ORDER_SERVICE_KEY_SHA256 = "b0a681124643e6144f8f373b0ab9338f8d9999598d6355c8244e8014ba3c15ff";

	private InternalTestKeys() {
	}

	public static RequestPostProcessor orderServiceKey() {
		return apiKey(ORDER_SERVICE_KEY);
	}

	public static RequestPostProcessor apiKey(String key) {
		return request -> {
			request.addHeader(HEADER, key);
			return request;
		};
	}

}
