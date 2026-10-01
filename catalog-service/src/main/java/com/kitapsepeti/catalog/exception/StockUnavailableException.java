package com.kitapsepeti.catalog.exception;

import java.util.List;
import java.util.UUID;

/**
 * Rezervasyon yapılamadı (409): {@link ErrorCode#BOOK_NOT_AVAILABLE} (kitap yok veya yayında değil) ya da
 * {@link ErrorCode#INSUFFICIENT_STOCK}. {@code bookIds} yanıtta ProblemDetail uzantısı olarak döner; yalnızca
 * internal uçta kullanılır, id dışında kitap bilgisi içermez.
 */
public class StockUnavailableException extends ApiException {

	private final List<UUID> bookIds;

	public StockUnavailableException(ErrorCode code, List<UUID> bookIds) {
		super(code);
		if (code != ErrorCode.BOOK_NOT_AVAILABLE && code != ErrorCode.INSUFFICIENT_STOCK) {
			throw new IllegalArgumentException("Not a stock availability code: " + code);
		}
		this.bookIds = List.copyOf(bookIds);
	}

	public List<UUID> getBookIds() {
		return this.bookIds;
	}

}
