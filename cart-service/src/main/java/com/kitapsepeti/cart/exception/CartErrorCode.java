package com.kitapsepeti.cart.exception;

import java.util.List;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * cart-service'e özgü hata kodları; ortak kodlar {@link CommonErrorCode}'da.
 * Kod adı ProblemDetail yanıtında {@code code} alanı olarak yazılır.
 * Stack trace yalnızca {@link Level#ERROR} seviyesindeki kodlarda loglanır.
 */
public enum CartErrorCode implements ErrorCode {

	CART_LINE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, Level.INFO, "Cart already contains the maximum number of different books."),
	CART_QUANTITY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, Level.INFO, "Quantity exceeds the maximum allowed per book."),
	BOOK_NOT_AVAILABLE(HttpStatus.CONFLICT, Level.INFO, "Book is not available for sale."),
	CATALOG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Level.WARN, "Catalog is temporarily unavailable; retry later.");

	/**
	 * Bu servisin döndürebildiği tüm kodlar, OpenAPI {@code Problem.code} enum'undaki sırayla. Sözleşme yayımlandıktan
	 * sonra sıra korunur; yeni kod cart kodlarının sonuna eklenir.
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
			CART_LINE_LIMIT_EXCEEDED,
			CART_QUANTITY_LIMIT_EXCEEDED,
			BOOK_NOT_AVAILABLE,
			CATALOG_UNAVAILABLE,
			CommonErrorCode.AUTHENTICATION_UNAVAILABLE,
			CommonErrorCode.INTERNAL_ERROR);

	private final HttpStatus status;

	private final Level logLevel;

	private final String defaultDetail;

	CartErrorCode(HttpStatus status, Level logLevel, String defaultDetail) {
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
