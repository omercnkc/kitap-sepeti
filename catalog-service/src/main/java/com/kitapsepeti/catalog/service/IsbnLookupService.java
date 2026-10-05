package com.kitapsepeti.catalog.service;

import com.kitapsepeti.catalog.client.BookMetadataClient;
import com.kitapsepeti.catalog.dto.response.IsbnMetadataResponse;
import com.kitapsepeti.catalog.exception.BookMetadataNotFoundException;
import com.kitapsepeti.catalog.exception.InvalidFieldException;
import com.kitapsepeti.catalog.validation.Isbns;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class IsbnLookupService {

	private final BookMetadataClient bookMetadataClient;

	public IsbnLookupService(BookMetadataClient bookMetadataClient) {
		this.bookMetadataClient = bookMetadataClient;
	}

	public IsbnMetadataResponse lookup(String rawIsbn) {
		String normalized = Isbns.normalize(rawIsbn);
		if (normalized == null || normalized.isBlank() || !Isbns.isValid(normalized)) {
			throw new InvalidFieldException("isbn", "Must be a valid ISBN-10 or ISBN-13.");
		}
		try {
			return this.bookMetadataClient.findByIsbn(normalized)
				.orElseThrow(BookMetadataNotFoundException::new);
		}
		catch (RestClientException ex) {
			throw new BookMetadataNotFoundException();
		}
	}

}
