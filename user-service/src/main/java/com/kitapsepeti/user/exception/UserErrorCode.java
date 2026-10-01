package com.kitapsepeti.user.exception;

import java.util.List;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * user-service'e özgü hata kodları; ortak kodlar {@link CommonErrorCode}'da.
 * Kod adı ProblemDetail yanıtında {@code code} alanı olarak yazılır.
 * Stack trace yalnızca {@link Level#ERROR} seviyesindeki kodlarda loglanır.
 */
public enum UserErrorCode implements ErrorCode {

	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, Level.INFO, "Email is already registered."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, Level.WARN, "Invalid email or password."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, Level.WARN, "Refresh token is invalid or expired."),
	ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, Level.WARN, "Account is suspended."),
	DEFAULT_ADDRESS_REQUIRED(HttpStatus.CONFLICT, Level.INFO, "An address list must keep one default address.");

	/**
	 * Bu servisin döndürebildiği tüm kodlar, OpenAPI {@code Problem.code} enum'undaki sırayla. Sıra yayımlanmış
	 * sözleşmeyle (docs/api) aynı kalmalı; yeni kod sona eklenir. AUTHENTICATION_UNAVAILABLE yok: token'ı kendi
	 * anahtarıyla doğrular, JWKS'e bağlı değildir.
	 */
	public static final List<ErrorCode> API_CODES = List.of(
			EMAIL_ALREADY_EXISTS,
			INVALID_CREDENTIALS,
			INVALID_REFRESH_TOKEN,
			ACCOUNT_SUSPENDED,
			CommonErrorCode.RESOURCE_NOT_FOUND,
			CommonErrorCode.VALIDATION_FAILED,
			CommonErrorCode.MALFORMED_REQUEST,
			CommonErrorCode.NOT_FOUND,
			CommonErrorCode.METHOD_NOT_ALLOWED,
			CommonErrorCode.NOT_ACCEPTABLE,
			CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
			CommonErrorCode.CONFLICT,
			DEFAULT_ADDRESS_REQUIRED,
			CommonErrorCode.UNAUTHORIZED,
			CommonErrorCode.FORBIDDEN,
			CommonErrorCode.INTERNAL_ERROR);

	private final HttpStatus status;

	private final Level logLevel;

	private final String defaultDetail;

	UserErrorCode(HttpStatus status, Level logLevel, String defaultDetail) {
		this.status = status;
		this.logLevel = logLevel;
		this.defaultDetail = defaultDetail;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public Level logLevel() {
		return this.logLevel;
	}

	@Override
	public String defaultDetail() {
		return this.defaultDetail;
	}

}
