package com.kitapsepeti.order.client;

import java.util.List;
import java.util.UUID;

/**
 * Karşı servisin 2xx olmayan yanıtı ({@link ProblemErrorDecoder}). Mesaj yalnızca durum ve kodu içerir: URL, yol
 * değişkeni (sipariş id'si) ve gövde YOK.
 */
public class RemoteProblemException extends RuntimeException {

	private final int status;

	private final transient String code;

	private final transient List<UUID> bookIds;

	public RemoteProblemException(int status, String code, List<UUID> bookIds) {
		super("Remote service responded with HTTP " + status + ((code != null) ? " " + code : ""), null, false, false);
		this.status = status;
		this.code = code;
		this.bookIds = List.copyOf(bookIds);
	}

	public int status() {
		return this.status;
	}

	/** ProblemDetail {@code code}; gövde ProblemDetail değilse null. */
	public String code() {
		return this.code;
	}

	/** Catalog stok hatalarının {@code bookIds} alanı; yoksa boş. */
	public List<UUID> bookIds() {
		return this.bookIds;
	}

}
