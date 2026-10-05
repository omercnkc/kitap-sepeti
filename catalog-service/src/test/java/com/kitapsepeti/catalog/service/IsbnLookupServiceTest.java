package com.kitapsepeti.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import com.kitapsepeti.catalog.client.BookMetadataClient;
import com.kitapsepeti.catalog.dto.response.IsbnMetadataResponse;
import com.kitapsepeti.catalog.exception.BookMetadataNotFoundException;
import com.kitapsepeti.catalog.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;

class IsbnLookupServiceTest {

	private static final String VALID = "9786053600770";

	@Test
	void lookupNormalizesAndReturnsMetadata() {
		IsbnMetadataResponse expected = new IsbnMetadataResponse(VALID, "Kar", null, null, 200,
				List.of("Orhan Pamuk"), List.of("YKY"));
		IsbnLookupService service = new IsbnLookupService(isbn -> Optional.of(expected));

		assertThat(service.lookup("978-605-360-077-0")).isEqualTo(expected);
	}

	@Test
	void lookupRejectsInvalidIsbn() {
		IsbnLookupService service = new IsbnLookupService(isbn -> Optional.empty());

		assertThatThrownBy(() -> service.lookup("123"))
			.isInstanceOf(InvalidFieldException.class)
			.satisfies(ex -> assertThat(((InvalidFieldException) ex).getField()).isEqualTo("isbn"));
	}

	@Test
	void lookupThrowsWhenProviderHasNoData() {
		BookMetadataClient missing = isbn -> Optional.empty();
		IsbnLookupService service = new IsbnLookupService(missing);

		assertThatThrownBy(() -> service.lookup(VALID)).isInstanceOf(BookMetadataNotFoundException.class);
	}

}
