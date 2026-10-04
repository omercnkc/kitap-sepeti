package com.kitapsepeti.order.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Yalnızca {@code /internal/**} isteklerine servis anahtarını ekler. Catalog'un public kitap okuması anahtarsız gider:
 * gerekmeyen yere sır taşınmaz. Gelen isteğin başlıkları (Authorization dahil) KOPYALANMAZ; kullanıcı token'ı hiçbir
 * istekte yer almaz.
 */
class InternalApiKeyInterceptor implements RequestInterceptor {

	static final String INTERNAL_PREFIX = "/internal/";

	private final InternalApiKey key;

	InternalApiKeyInterceptor(InternalApiKey key) {
		this.key = key;
	}

	@Override
	public void apply(RequestTemplate template) {
		if (template.path().startsWith(INTERNAL_PREFIX)) {
			template.header(InternalApiKey.HEADER, this.key.value());
		}
	}

}
