package com.kitapsepeti.user.exception;

import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * API'nin döndürdüğü hata kodları. Her kod HTTP durumunu, log seviyesini ve istemciye
 * gösterilecek varsayılan (genel, veri içermeyen) açıklamayı tek yerde tutar.
 * Kod adı ProblemDetail yanıtında {@code code} alanı olarak yazılır.
 * Stack trace yalnızca {@link Level#ERROR} seviyesindeki kodlarda loglanır.
 */
public enum ErrorCode {

	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, Level.INFO, "Email is already registered."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, Level.WARN, "Invalid email or password."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, Level.WARN, "Refresh token is invalid or expired."),
	ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, Level.WARN, "Account is suspended."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, Level.INFO, "Requested resource was not found."),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, Level.INFO, "Request validation failed."),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, Level.INFO, "Request could not be read."),
	NOT_FOUND(HttpStatus.NOT_FOUND, Level.INFO, "No endpoint matches this path."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, Level.INFO, "HTTP method is not supported for this endpoint."),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, Level.INFO, "Requested response media type is not supported."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, Level.INFO, "Content type is not supported."),
	CONFLICT(HttpStatus.CONFLICT, Level.INFO, "Request conflicts with the current state of the resource."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, Level.WARN, "Authentication is required."),
	FORBIDDEN(HttpStatus.FORBIDDEN, Level.WARN, "Access is denied."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Level.ERROR, "An unexpected error occurred.");

	private final HttpStatus status;

	private final Level logLevel;

	private final String defaultDetail;

	ErrorCode(HttpStatus status, Level logLevel, String defaultDetail) {
		this.status = status;
		this.logLevel = logLevel;
		this.defaultDetail = defaultDetail;
	}

	public HttpStatus status() {
		return this.status;
	}

	public Level logLevel() {
		return this.logLevel;
	}

	public String defaultDetail() {
		return this.defaultDetail;
	}

}
