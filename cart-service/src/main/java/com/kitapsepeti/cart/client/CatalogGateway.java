package com.kitapsepeti.cart.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.kitapsepeti.cart.exception.BookNotAvailableException;
import com.kitapsepeti.cart.exception.CatalogUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Component;

/**
 * Servis katmanının Catalog'a tek temas noktası; Feign tipleri ve hataları bu sınıfın dışına çıkmaz.
 * Hata eşlemesi burada (ErrorDecoder'da değil), çünkü aynı durum kodu metoda göre farklı anlam taşır: kitap okumada
 * 404 "satışta değil", toplu okumada ise bizim hatamızdır.
 * <ul>
 * <li>5xx, zaman aşımı, bağlantı hatası, okunamayan/eksik yanıt → {@link CatalogUnavailableException} (503)</li>
 * <li>{@link #requireAvailableBook}: 404 ya da {@code inStock=false} → {@link BookNotAvailableException} (409)</li>
 * <li>diğer 4xx → {@link IllegalStateException} (500); mesajda URL/kitap id'si yok, Feign hatası neden olarak eklenmez</li>
 * </ul>
 * Çağrılar {@code catalog} circuit breaker'ından geçer: yalnızca {@link CatalogUnavailableException}'a dönüşen teknik
 * hatalar hata sayılır, 4xx ve {@code inStock=false} başarıdır. Devre açıkken Catalog'a istek gitmez ve aynı
 * {@link CatalogUnavailableException} (nedeni {@link CallNotPermittedException}) fırlatılır.
 */
@Component
public class CatalogGateway {

	/** Catalog {@code GET /api/books/lookup} üst sınırı (sözleşmedeki {@code maxItems}). */
	public static final int MAX_LOOKUP_IDS = 50;

	private final CatalogClient client;

	private final CircuitBreaker breaker;

	public CatalogGateway(CatalogClient client, CircuitBreaker catalogCircuitBreaker) {
		this.client = client;
		this.breaker = catalogCircuitBreaker;
	}

	/**
	 * Sepete eklenecek kitabın güncel fiyatı ve stok durumu.
	 * @throws BookNotAvailableException kitap yok, yayında değil ya da stokta değil
	 * @throws CatalogUnavailableException Catalog'a ulaşılamadı ya da yanıtı okunamadı
	 */
	public CatalogBook requireAvailableBook(UUID bookId) {
		return guarded(() -> fetchAvailableBook(bookId));
	}

	private CatalogBook fetchAvailableBook(UUID bookId) {
		CatalogBook book;
		try {
			book = this.client.getBook(bookId);
		}
		catch (FeignException ex) {
			throw translate(ex, Operation.GET_BOOK);
		}
		if (book == null || !book.id().equals(bookId)) {
			throw new CatalogUnavailableException(new InvalidCatalogResponseException("unexpected book response"));
		}
		if (!book.inStock()) {
			throw new BookNotAvailableException();
		}
		return book;
	}

	/**
	 * Verilen kitaplardan yayında olanlar, tek istekte. Map'te olmayan id = satışta değil (yok, taslak, arşiv).
	 * Boş koleksiyonda Catalog çağrılmaz. Tekrarlı id'ler bir kez gönderilir; sınır tekil id sayısına uygulanır.
	 * @throws IllegalArgumentException {@value #MAX_LOOKUP_IDS}'den fazla tekil id
	 * @throws CatalogUnavailableException Catalog'a ulaşılamadı ya da yanıtı okunamadı
	 */
	public Map<UUID, CatalogBook> lookup(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		List<UUID> distinctIds = List.copyOf(new LinkedHashSet<>(ids));
		if (distinctIds.size() > MAX_LOOKUP_IDS) {
			throw new IllegalArgumentException(
					"At most " + MAX_LOOKUP_IDS + " book ids per lookup, got " + distinctIds.size());
		}
		CatalogBookLookup response = guarded(() -> fetchLookup(distinctIds));
		Set<UUID> requested = new HashSet<>(distinctIds);
		Map<UUID, CatalogBook> books = new LinkedHashMap<>();
		for (CatalogBook book : response.items()) {
			if (requested.contains(book.id())) {
				books.putIfAbsent(book.id(), book);
			}
		}
		return Collections.unmodifiableMap(books);
	}

	private CatalogBookLookup fetchLookup(List<UUID> distinctIds) {
		CatalogBookLookup response;
		try {
			response = this.client.lookup(distinctIds);
		}
		catch (FeignException ex) {
			throw translate(ex, Operation.LOOKUP);
		}
		if (response == null || response.items().contains(null)) {
			throw new CatalogUnavailableException(new InvalidCatalogResponseException("unexpected lookup response"));
		}
		return response;
	}

	private <T> T guarded(Supplier<T> call) {
		if (!this.breaker.tryAcquirePermission()) {
			throw new CatalogUnavailableException(CallNotPermittedException.createCallNotPermittedException(this.breaker));
		}
		long start = System.nanoTime();
		try {
			T result = call.get();
			this.breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
			return result;
		}
		catch (CatalogUnavailableException ex) {
			this.breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
			throw ex;
		}
		catch (BookNotAvailableException | IllegalStateException ex) {
			// Catalog yanıt verdi (4xx ya da stokta değil): devre açısından başarı.
			this.breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
			throw ex;
		}
		catch (Throwable ex) {
			this.breaker.releasePermission();
			throw ex;
		}
	}

	private static RuntimeException translate(FeignException ex, Operation operation) {
		int status = ex.status();
		// RetryableException: bağlantı/zaman aşımı (status -1) ya da Retry-After'lı 503. 2xx: gövde okunamadı/çözülemedi.
		if (ex instanceof RetryableException || status < 0 || status >= 500 || (status >= 200 && status < 300)) {
			return new CatalogUnavailableException(ex);
		}
		if (status == 404 && operation == Operation.GET_BOOK) {
			return new BookNotAvailableException();
		}
		return new IllegalStateException("Catalog rejected " + operation + " request with HTTP " + status);
	}

	private enum Operation {

		GET_BOOK, LOOKUP

	}

}
