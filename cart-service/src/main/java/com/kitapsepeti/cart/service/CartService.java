package com.kitapsepeti.cart.service;

import java.util.UUID;

import com.kitapsepeti.cart.client.CatalogBook;
import com.kitapsepeti.cart.client.CatalogGateway;
import com.kitapsepeti.cart.dto.request.AddCartItemRequest;
import com.kitapsepeti.cart.dto.request.UpdateCartItemRequest;
import com.kitapsepeti.cart.dto.response.CartResponse;
import com.kitapsepeti.common.error.DbConstraints;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Sepet kullanım senaryoları. Bilerek transaction'sız: Catalog çağrıları (ağ, saniyeler sürebilir) DB transaction'ı
 * ve satır kilidi tutulurken yapılmaz. Sıra: Catalog doğrulaması → kısa DB transaction'ı ({@link CartTransactions})
 * → Catalog'la birleştirilmiş görünüm ({@link CartViewAssembler}).
 */
@Service
public class CartService {

	static final String ACTIVE_CART_CONSTRAINT = "uk_carts_active_user";

	private final CatalogGateway catalog;

	private final CartTransactions transactions;

	private final CartViewAssembler assembler;

	public CartService(CatalogGateway catalog, CartTransactions transactions, CartViewAssembler assembler) {
		this.catalog = catalog;
		this.transactions = transactions;
		this.assembler = assembler;
	}

	/** Aktif sepet; yoksa boş sepet (oluşturulmaz, Catalog çağrılmaz). */
	public CartResponse getCart(UUID userId) {
		return this.assembler.assemble(this.transactions.findActive(userId));
	}

	/**
	 * Kitap satışta değilse ({@code BOOK_NOT_AVAILABLE}) ya da Catalog'a ulaşılamazsa ({@code CATALOG_UNAVAILABLE})
	 * sepete hiç dokunulmaz.
	 * <p>
	 * Sepeti olmayan kullanıcının eşzamanlı iki ilk eklemesinde ikinci INSERT {@code uk_carts_active_user}'ı ihlal
	 * eder. Transaction geri alınmıştır; ekleme bir kez, YENİ transaction'da tekrarlanır ve bu kez artık var olan
	 * sepeti kilitler. İkinci ihlal (beklenmez) ve diğer kısıt ihlalleri hata handler'ına gider (409 CONFLICT).
	 */
	public CartResponse addItem(UUID userId, AddCartItemRequest request) {
		CatalogBook book = this.catalog.requireAvailableBook(request.bookId());
		CartContents contents;
		try {
			contents = this.transactions.addItem(userId, book, request.quantity());
		}
		catch (DataIntegrityViolationException ex) {
			if (!DbConstraints.isViolated(ex, ACTIVE_CART_CONSTRAINT)) {
				throw ex;
			}
			contents = this.transactions.addItem(userId, book, request.quantity());
		}
		return this.assembler.assemble(contents);
	}

	/** Catalog'a sorulmaz; anlık görüntü korunur. Sepet yoksa ya da kitap sepette değilse 404. */
	public CartResponse changeQuantity(UUID userId, UUID bookId, UpdateCartItemRequest request) {
		return this.assembler.assemble(this.transactions.changeQuantity(userId, bookId, request.quantity()));
	}

	/** Idempotent; sepet yoksa boş sepet döner (oluşturulmaz). */
	public CartResponse removeItem(UUID userId, UUID bookId) {
		return this.assembler.assemble(this.transactions.removeItem(userId, bookId));
	}

	/** Sepet aktif ve boş kalır; yoksa oluşturulmaz. Yanıt her zaman boş sepet (Catalog çağrılmaz). */
	public CartResponse clear(UUID userId) {
		return this.assembler.assemble(this.transactions.clear(userId));
	}

}
