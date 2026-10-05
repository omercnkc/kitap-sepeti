package com.kitapsepeti.catalog.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.kitapsepeti.catalog.dto.response.IsbnMetadataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Open Library Books API ({@code /api/books?bibkeys=ISBN:…&jscmd=data}). Anahtar/ücret yok.
 */
@Component
public class OpenLibraryBookMetadataClient implements BookMetadataClient {

	private static final Logger log = LoggerFactory.getLogger(OpenLibraryBookMetadataClient.class);

	private final RestClient openLibraryRestClient;

	public OpenLibraryBookMetadataClient(RestClient openLibraryRestClient) {
		this.openLibraryRestClient = openLibraryRestClient;
	}

	@Override
	public Optional<IsbnMetadataResponse> findByIsbn(String normalizedIsbn) {
		String bibkey = "ISBN:" + normalizedIsbn;
		JsonNode root;
		try {
			root = this.openLibraryRestClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/books")
					.queryParam("bibkeys", bibkey)
					.queryParam("format", "json")
					.queryParam("jscmd", "data")
					.build())
				.retrieve()
				.body(JsonNode.class);
		}
		catch (RestClientException ex) {
			log.warn("Open Library request failed for ISBN lookup");
			throw ex;
		}
		if (root == null || root.isNull() || root.isEmpty()) {
			return Optional.empty();
		}
		JsonNode book = root.get(bibkey);
		if (book == null || book.isNull() || book.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(mapBook(normalizedIsbn, book));
	}

	static IsbnMetadataResponse mapBook(String isbn, JsonNode book) {
		String title = textOrNull(book.get("title"));
		String description = firstNonBlank(textOrNull(book.get("notes")), excerptText(book));
		String coverUrl = coverUrl(book, isbn);
		Integer pageCount = intOrNull(book.get("number_of_pages"));
		List<String> authors = names(book.get("authors"));
		List<String> publishers = names(book.get("publishers"));
		return new IsbnMetadataResponse(isbn, title, description, coverUrl, pageCount, authors, publishers);
	}

	private static List<String> names(JsonNode array) {
		if (array == null || !array.isArray()) {
			return List.of();
		}
		List<String> names = new ArrayList<>(array.size());
		for (JsonNode node : array) {
			String name = textOrNull(node.get("name"));
			if (name != null && !name.isBlank()) {
				names.add(name.trim());
			}
		}
		return Collections.unmodifiableList(names);
	}

	private static String coverUrl(JsonNode book, String isbn) {
		JsonNode cover = book.get("cover");
		if (cover != null && cover.isObject()) {
			String large = textOrNull(cover.get("large"));
			if (large != null) {
				return large;
			}
			String medium = textOrNull(cover.get("medium"));
			if (medium != null) {
				return medium;
			}
		}
		return "https://covers.openlibrary.org/b/isbn/" + isbn + "-L.jpg";
	}

	private static String excerptText(JsonNode book) {
		JsonNode excerpts = book.get("excerpts");
		if (excerpts == null || !excerpts.isArray() || excerpts.isEmpty()) {
			return null;
		}
		return textOrNull(excerpts.get(0).get("text"));
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.isBlank()) {
			return a.trim();
		}
		if (b != null && !b.isBlank()) {
			return b.trim();
		}
		return null;
	}

	private static String textOrNull(JsonNode node) {
		if (node == null || node.isNull() || !node.isValueNode()) {
			return null;
		}
		String text = node.asText();
		return text == null || text.isBlank() ? null : text;
	}

	private static Integer intOrNull(JsonNode node) {
		if (node == null || node.isNull() || !node.isNumber()) {
			return null;
		}
		int value = node.asInt();
		return value > 0 ? value : null;
	}

}
