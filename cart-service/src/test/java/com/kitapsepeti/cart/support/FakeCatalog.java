package com.kitapsepeti.cart.support;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kitapsepeti.cart.support.CatalogStub.Request;
import com.kitapsepeti.cart.support.CatalogStub.Response;

/**
 * {@link CatalogStub} için kitap tablosu gibi davranan yanıtlayıcı. Haritadaki kitap "yayında"dır: detayda döner,
 * lookup'ta (istenmişse) listelenir. Haritada olmayan kitap taslak/silinmiş sayılır: detay 404, lookup'ta yok.
 * {@link #failWith} ile tüm istekler aynı (hata) yanıtı alır.
 */
public final class FakeCatalog implements Function<Request, Response> {

	private static final String BOOK_PATH = "/api/books/";

	private static final String LOOKUP_PATH = "/api/books/lookup";

	private final Map<UUID, Book> books = new ConcurrentHashMap<>();

	private volatile Response outage;

	public record Book(UUID id, String title, String price, String currency, String coverUrl, boolean inStock) {

		public Book withPrice(String newPrice) {
			return new Book(id, title, newPrice, currency, coverUrl, inStock);
		}

		public Book withTitle(String newTitle) {
			return new Book(id, newTitle, price, currency, coverUrl, inStock);
		}

		public Book outOfStock() {
			return new Book(id, title, price, currency, coverUrl, false);
		}

	}

	/** Yayında, stokta, TRY, kapaksız yeni kitap. */
	public Book publish(String title, String price) {
		return put(new Book(UUID.randomUUID(), title, price, "TRY", null, true));
	}

	public Book put(Book book) {
		this.books.put(book.id(), book);
		return book;
	}

	/** Kitabı yayından kaldırır (taslağa çekilmiş gibi). */
	public void unpublish(UUID id) {
		this.books.remove(id);
	}

	public void failWith(Response response) {
		this.outage = response;
	}

	public void recover() {
		this.outage = null;
	}

	@Override
	public Response apply(Request request) {
		Response failure = this.outage;
		if (failure != null) {
			return failure;
		}
		if (LOOKUP_PATH.equals(request.path())) {
			String items = Arrays.stream(Objects.requireNonNullElse(request.query(), "").split("&"))
				.filter(param -> param.startsWith("ids="))
				.map(param -> this.books.get(UUID.fromString(param.substring(4))))
				.filter(Objects::nonNull)
				.map(FakeCatalog::summaryJson)
				.collect(Collectors.joining(","));
			return Response.json(200, "{\"items\":[" + items + "]}");
		}
		if (request.path().startsWith(BOOK_PATH)) {
			Book book = this.books.get(UUID.fromString(request.path().substring(BOOK_PATH.length())));
			return (book != null) ? Response.json(200, detailJson(book)) : Response.problem(404);
		}
		return Response.problem(404);
	}

	private static String detailJson(Book book) {
		return """
				{"id":"%s","title":"%s","isbn":"9789753638029","pageCount":160,"priceAmount":%s,"currency":"%s",\
				"coverUrl":%s,"inStock":%s,"publisher":{"id":"%s","name":"YKY","slug":"yky"},"authors":[],"categories":[]}"""
			.formatted(book.id(), book.title(), book.price(), book.currency(), quoted(book.coverUrl()), book.inStock(),
					UUID.randomUUID());
	}

	private static String summaryJson(Book book) {
		return """
				{"id":"%s","title":"%s","coverUrl":%s,"priceAmount":%s,"currency":"%s","inStock":%s,"authors":[]}"""
			.formatted(book.id(), book.title(), quoted(book.coverUrl()), book.price(), book.currency(), book.inStock());
	}

	private static String quoted(String value) {
		return (value == null) ? "null" : "\"" + value + "\"";
	}

}
