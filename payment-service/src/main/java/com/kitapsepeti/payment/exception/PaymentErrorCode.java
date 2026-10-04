package com.kitapsepeti.payment.exception;

import java.util.List;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * payment-service'e özgü hata kodları; ortak kodlar {@link CommonErrorCode}'da.
 * Kod adı ProblemDetail yanıtında {@code code} alanı olarak yazılır.
 * Stack trace yalnızca {@link Level#ERROR} seviyesindeki kodlarda loglanır.
 */
public enum PaymentErrorCode implements ErrorCode {

	PAYMENT_ORDER_MISMATCH(HttpStatus.CONFLICT, Level.INFO,
			"A payment already exists for this order with a different user, amount or currency."),
	PAYMENT_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Level.WARN,
			"Payment provider is temporarily unavailable; retry later."),
	/** Log satırını {@link GlobalExceptionHandler} kendisi yazar (yalnızca sağlayıcı adı). */
	WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, Level.WARN, "Webhook signature is missing or invalid."),
	UNKNOWN_PAYMENT(HttpStatus.BAD_REQUEST, Level.WARN, "Webhook refers to an unknown payment."),
	AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, Level.WARN, "Webhook amount or currency does not match the payment."),
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, Level.INFO, "Request body is too large.");

	/**
	 * Bu servisin döndürebildiği tüm kodlar (OpenAPI {@code Problem.code} enum'u eklenince bu sırayla yazılır).
	 * Sözleşme yayımlandıktan sonra sıra korunur; yeni kod payment kodlarının sonuna eklenir.
	 */
	public static final List<ErrorCode> API_CODES = List.of(
			CommonErrorCode.VALIDATION_FAILED,
			CommonErrorCode.MALFORMED_REQUEST,
			CommonErrorCode.UNAUTHORIZED,
			CommonErrorCode.FORBIDDEN,
			CommonErrorCode.NOT_FOUND,
			CommonErrorCode.RESOURCE_NOT_FOUND,
			CommonErrorCode.METHOD_NOT_ALLOWED,
			CommonErrorCode.NOT_ACCEPTABLE,
			CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
			CommonErrorCode.CONFLICT,
			PAYMENT_ORDER_MISMATCH,
			PAYMENT_PROVIDER_UNAVAILABLE,
			WEBHOOK_SIGNATURE_INVALID,
			UNKNOWN_PAYMENT,
			AMOUNT_MISMATCH,
			PAYLOAD_TOO_LARGE,
			CommonErrorCode.INTERNAL_ERROR);

	private final HttpStatus status;

	private final Level logLevel;

	private final String defaultDetail;

	PaymentErrorCode(HttpStatus status, Level logLevel, String defaultDetail) {
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
