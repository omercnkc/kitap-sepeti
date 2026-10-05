package com.kitapsepeti.catalog.client;

import java.util.Optional;

import com.kitapsepeti.catalog.dto.response.IsbnMetadataResponse;

/** Dış kitap metadata kaynağı (Open Library). */
public interface BookMetadataClient {

	/**
	 * Normalize edilmiş geçerli ISBN için metadata. Bulunamazsa empty; ağ/parse hatalarında exception.
	 */
	Optional<IsbnMetadataResponse> findByIsbn(String normalizedIsbn);

}
