package com.kitapsepeti.catalog.exception;

import java.util.List;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * catalog-service'e özgü hata kodları; ortak kodlar {@link CommonErrorCode}'da.
 * Kod adı ProblemDetail yanıtında {@code code} alanı olarak yazılır.
 * Stack trace yalnızca {@link Level#ERROR} seviyesindeki kodlarda loglanır.
 */
public enum CatalogErrorCode implements ErrorCode {

	SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, Level.INFO, "Slug is already in use."),
	ISBN_ALREADY_EXISTS(HttpStatus.CONFLICT, Level.INFO, "ISBN is already registered."),
	RESOURCE_IN_USE(HttpStatus.CONFLICT, Level.INFO, "Resource is referenced by other records and cannot be deleted."),
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, Level.INFO, "Resource was modified by another request; reload and retry."),
	CATEGORY_CYCLE(HttpStatus.CONFLICT, Level.INFO, "Category cannot be moved under itself or its descendants."),
	BOOK_NOT_PUBLISHABLE(HttpStatus.CONFLICT, Level.INFO, "Book does not meet the requirements for publishing."),
	STOCK_BELOW_RESERVED(HttpStatus.CONFLICT, Level.INFO, "Stock cannot be reduced below the reserved quantity."),
	INSUFFICIENT_STOCK(HttpStatus.CONFLICT, Level.INFO, "Not enough stock for one or more books."),
	BOOK_NOT_AVAILABLE(HttpStatus.CONFLICT, Level.INFO, "One or more books are not available for sale."),
	RESERVATION_MISMATCH(HttpStatus.CONFLICT, Level.INFO, "A different reservation already exists for this order."),
	RESERVATION_RELEASED(HttpStatus.CONFLICT, Level.INFO, "Reservation was released and can no longer be committed."),
	RESERVATION_COMMITTED(HttpStatus.CONFLICT, Level.INFO, "Reservation was already committed and cannot be released."),
	BOOK_METADATA_NOT_FOUND(HttpStatus.NOT_FOUND, Level.INFO, "No book metadata found for this ISBN.");

	/**
	 * Bu servisin döndürebildiği tüm kodlar, OpenAPI {@code Problem.code} enum'undaki sırayla. Sıra yayımlanmış
	 * sözleşmeyle (docs/api) aynı kalmalı; yeni kod listenin sonuna eklenir.
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
			SLUG_ALREADY_EXISTS,
			ISBN_ALREADY_EXISTS,
			RESOURCE_IN_USE,
			CONCURRENT_MODIFICATION,
			CATEGORY_CYCLE,
			BOOK_NOT_PUBLISHABLE,
			STOCK_BELOW_RESERVED,
			INSUFFICIENT_STOCK,
			BOOK_NOT_AVAILABLE,
			RESERVATION_MISMATCH,
			RESERVATION_RELEASED,
			RESERVATION_COMMITTED,
			BOOK_METADATA_NOT_FOUND,
			CommonErrorCode.AUTHENTICATION_UNAVAILABLE,
			CommonErrorCode.INTERNAL_ERROR);

	private final HttpStatus status;

	private final Level logLevel;

	private final String defaultDetail;

	CatalogErrorCode(HttpStatus status, Level logLevel, String defaultDetail) {
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
