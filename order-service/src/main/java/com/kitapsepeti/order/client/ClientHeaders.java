package com.kitapsepeti.order.client;

/** Feign istemcilerinin ortak sabit başlıkları ({@code @RequestMapping(headers = ...)} biçiminde). */
public final class ClientHeaders {

	/** Hata yanıtları {@code application/problem+json}; yalnızca {@code application/json} istemek 406'ya yol açabilir. */
	public static final String ACCEPT_JSON = "Accept=application/json, application/problem+json";

	private ClientHeaders() {
	}

}
