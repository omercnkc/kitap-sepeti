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
		assertThat(mapped.subjects()).isEmpty();
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
		assertThat(mapped.subjects()).isEmpty();
	}

	@Test
	void mapBookUsesDescriptionNotExcerptAndReadsSubjects() throws Exception {
		var book = jsonMapper.readTree("""
				{
				  "title": "The Invention of Hugo Cabret",
				  "description": {
				    "type": "/type/text",
				    "value": "ORPHAN, CLOCK KEEPER, THIEF."
				  },
				  "notes": "Should not win when description present.",
				  "excerpts": [{"text": "THE STORY I AM ABOUT TO SHARE"}],
				  "subjects": [
				    {"name": "Juvenile fiction"},
				    {"name": "Children"}
				  ],
				  "authors": [{"name": "Brian Selznick"}],
				  "publishers": [{"name": "Scholastic"}],
				  "works": [{"key": "/works/OL123W"}]
				}
				""");

		IsbnMetadataResponse mapped = OpenLibraryBookMetadataClient.mapBook("9788467520446", book);

		assertThat(mapped.title()).isEqualTo("The Invention of Hugo Cabret");
		assertThat(mapped.description()).isEqualTo("ORPHAN, CLOCK KEEPER, THIEF.");
		assertThat(mapped.description()).doesNotContain("THE STORY I AM ABOUT TO SHARE");
		assertThat(mapped.subjects()).containsExactly("Juvenile fiction", "Children");
	}

	@Test
	void mapBookIgnoresExcerptWhenDescriptionAndNotesMissing() throws Exception {
		var excerptOnly = jsonMapper.readTree("""
				{
				  "title": "E",
				  "excerpts": [{"text": "THE STORY I AM ABOUT TO SHARE"}]
				}
				""");
		assertThat(OpenLibraryBookMetadataClient.mapBook("9788467520446", excerptOnly).description()).isNull();
	}

	@Test
	void mapBookUsesNotesWhenDescriptionMissing() throws Exception {
		var notesOnly = jsonMapper.readTree("""
				{ "title": "N", "notes": "From notes.", "excerpts": [{"text": "From excerpt."}] }
				""");
		assertThat(OpenLibraryBookMetadataClient.mapBook("9780306406157", notesOnly).description())
			.isEqualTo("From notes.");
	}

	@Test
	void descriptionFieldAcceptsStringOrValueObject() {
		assertThat(OpenLibraryBookMetadataClient.descriptionField(jsonMapper.valueToTree("Plain")))
			.isEqualTo("Plain");
		assertThat(OpenLibraryBookMetadataClient.descriptionField(
				jsonMapper.createObjectNode().put("value", " Nested ")))
			.isEqualTo("Nested");
		assertThat(OpenLibraryBookMetadataClient.descriptionField(null)).isNull();
	}

}
