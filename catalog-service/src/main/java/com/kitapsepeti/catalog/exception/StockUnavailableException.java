package com.kitapsepeti.catalog.exception;

import java.util.List;
import java.util.UUID;

import com.kitapsepeti.common.error.ApiException;

/**
 * Rezervasyon yapılamadı (409): {@link CatalogErrorCode#BOOK_NOT_AVAILABLE} (kitap yok veya yayında değil) ya da
 * {@link CatalogErrorCode#INSUFFICIENT_STOCK}. {@code bookIds} yanıtta ProblemDetail uzantısı olarak döner; yalnızca
 * internal uçta kullanılır, id dışında kitap bilgisi içermez.
 */
public class StockUnavailableException extends ApiException {

	private final List<UUID> bookIds;

	public StockUnavailableException(CatalogErrorCode code, List<UUID> bookIds) {
		super(code);
		if (code != CatalogErrorCode.BOOK_NOT_AVAILABLE && code != CatalogErrorCode.INSUFFICIENT_STOCK) {
			throw new IllegalArgumentException("Not a stock availability code: " + code);
		}
		this.bookIds = List.copyOf(bookIds);
	}

	public List<UUID> getBookIds() {
		return this.bookIds;
	}

}
