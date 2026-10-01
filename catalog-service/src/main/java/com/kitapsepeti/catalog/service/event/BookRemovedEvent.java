package com.kitapsepeti.catalog.service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code BookRemoved} outbox olayının içeriği (bkz. {@code docs/events/book-removed.md}): yayındaki kitap
 * arşivlendi, artık listelenmemeli. Alan eklemek geriye uyumludur.
 */
public record BookRemovedEvent(int eventVersion, UUID bookId, Instant occurredAt) {

	public static final int VERSION = 1;

	public static final String TYPE = "BookRemoved";

}
