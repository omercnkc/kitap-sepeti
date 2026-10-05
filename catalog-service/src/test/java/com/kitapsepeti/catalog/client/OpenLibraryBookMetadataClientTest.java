package com.kitapsepeti.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitapsepeti.catalog.dto.response.IsbnMetadataResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OpenLibraryBookMetadataClientTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void mapBookReadsTitleAuthorsCoverAndPages() throws Exception {
		var book = jsonMapper.readTree("""
				{
				  "title": "Slow reading",
				  "notes": "A short note.",
				  "number_of_pages": 80,
				  "authors": [{"name": "John Miedema", "url": "https://openlibrary.org/authors/OL1A"}],
				  "publishers": [{"name": "Litwin Books"}],
				  "cover": {
				    "large": "https://covers.openlibrary.org/b/id/1-L.jpg",
				    "medium": "https://covers.openlibrary.org/b/id/1-M.jpg"
				  }
				}
				""");

		IsbnMetadataResponse mapped = OpenLibraryBookMetadataClient.mapBook("9780980200447", book);

		assertThat(mapped.isbn()).isEqualTo("9780980200447");
		assertThat(mapped.title()).isEqualTo("Slow reading");
		assertThat(mapped.description()).isEqualTo("A short note.");
		assertThat(mapped.pageCount()).isEqualTo(80);
		assertThat(mapped.authors()).containsExactly("John Miedema");
		assertThat(mapped.publishers()).containsExactly("Litwin Books");
		assertThat(mapped.coverUrl()).isEqualTo("https://covers.openlibrary.org/b/id/1-L.jpg");
	}

	@Test
	void mapBookFallsBackToIsbnCoverWhenCoverMissing() throws Exception {
		var book = jsonMapper.readTree("""
				{ "title": "No cover" }
				""");

		IsbnMetadataResponse mapped = OpenLibraryBookMetadataClient.mapBook("9780140328721", book);

		assertThat(mapped.coverUrl()).isEqualTo("https://covers.openlibrary.org/b/isbn/9780140328721-L.jpg");
		assertThat(mapped.authors()).isEmpty();
		assertThat(mapped.publishers()).isEmpty();
	}

}
