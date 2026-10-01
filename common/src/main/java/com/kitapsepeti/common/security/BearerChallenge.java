package com.kitapsepeti.common.security;

/**
 * 401 yanıtlarındaki {@code WWW-Authenticate} değerleri (RFC 6750 §3). Token hiç yoksa hata kodu
 * verilmez; geçersizse {@code invalid_token} eklenir. {@code error_description} bilinçli olarak yok
 * (neden reddedildiğini, örn. imza/süre ayrıntısını, dışarı vermez).
 */
public final class BearerChallenge {

	public static final String MISSING_TOKEN = "Bearer";

	public static final String INVALID_TOKEN = "Bearer error=\"invalid_token\"";

	private BearerChallenge() {
	}

}
