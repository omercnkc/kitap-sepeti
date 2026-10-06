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
 * Açıklama: work/edition {@code description} (string veya {@code .value}); notes isteğe bağlı.
 * {@code excerpts} asla description'a yazılmaz. Work overview varsa edition'a tercih edilir.
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
		return Optional.of(preferWorkOverview(mapBook(normalizedIsbn, book), book));
	}

	static IsbnMetadataResponse mapBook(String isbn, JsonNode book) {
		String title = textOrNull(book.get("title"));
		String description = firstNonBlank(descriptionField(book.get("description")), textOrNull(book.get("notes")));
		String coverUrl = coverUrl(book, isbn);
		Integer pageCount = intOrNull(book.get("number_of_pages"));
		List<String> authors = names(book.get("authors"));
		List<String> subjects = subjectNames(book.get("subjects"));
		return new IsbnMetadataResponse(isbn, title, description, coverUrl, pageCount, authors, subjects);
	}

	/**
	 * Work JSON'daki overview description edition/notes'a tercih edilir. Excerpt kullanılmaz.
	 * {@code jscmd=data} bazen {@code works} vermez; o durumda {@code /isbn/{isbn}.json} ile work key alınır.
	 * Edition subjects boşsa work subjects doldurulur.
	 */
	private IsbnMetadataResponse preferWorkOverview(IsbnMetadataResponse mapped, JsonNode book) {
		String workKey = workKey(book);
		if (workKey == null) {
			workKey = workKeyFromIsbnEdition(mapped.isbn());
		}
		if (workKey == null) {
			return mapped;
		}
		try {
			JsonNode work = this.openLibraryRestClient.get()
				.uri(workKey.endsWith(".json") ? workKey : workKey + ".json")
				.retrieve()
				.body(JsonNode.class);
			if (work == null || work.isNull()) {
				return mapped;
			}
			String workDescription = descriptionField(work.get("description"));
			List<String> subjects = mapped.subjects();
			if (subjects.isEmpty()) {
				subjects = subjectNames(work.get("subjects"));
			}
			if (workDescription == null && subjects.equals(mapped.subjects())) {
				return mapped;
			}
			return new IsbnMetadataResponse(mapped.isbn(), mapped.title(),
					workDescription != null ? workDescription : mapped.description(), mapped.coverUrl(),
					mapped.pageCount(), mapped.authors(), subjects);
		}
		catch (RestClientException ex) {
			log.warn("Open Library work request failed for ISBN description");
			return mapped;
		}
	}

	/** data API'de works yoksa edition kaydından work key. */
	private String workKeyFromIsbnEdition(String isbn) {
		try {
			JsonNode edition = this.openLibraryRestClient.get()
				.uri("/isbn/{isbn}.json", isbn)
				.retrieve()
				.body(JsonNode.class);
			return workKey(edition);
		}
		catch (RestClientException ex) {
			log.warn("Open Library isbn edition request failed for work key");
			return null;
		}
	}

	private static String workKey(JsonNode book) {
		if (book == null || book.isNull()) {
			return null;
		}
		JsonNode works = book.get("works");
		if (works == null || !works.isArray() || works.isEmpty()) {
			return null;
		}
		String key = textOrNull(works.get(0).get("key"));
		if (key == null || !key.startsWith("/")) {
			return null;
		}
		return key;
	}

	/** OL description: düz string veya {@code { "value": "…" }}. */
	static String descriptionField(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isTextual()) {
			String text = node.asText();
			return text == null || text.isBlank() ? null : text.trim();
		}
		if (node.isObject()) {
			String value = textOrNull(node.get("value"));
			return value == null ? null : value.trim();
		}
		return null;
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

	/** Edition subjects {@code {name}} veya work subjects düz string. */
	static List<String> subjectNames(JsonNode array) {
		if (array == null || !array.isArray()) {
			return List.of();
		}
		List<String> names = new ArrayList<>(array.size());
		for (JsonNode node : array) {
			String name = node != null && node.isTextual() ? textOrNull(node) : textOrNull(node.get("name"));
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

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
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
