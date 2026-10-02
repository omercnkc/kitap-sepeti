package com.kitapsepeti.cart.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.cart.client.CatalogBook;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.entity.CartItem;
import com.kitapsepeti.cart.entity.CartStatus;
import com.kitapsepeti.cart.exception.CartLimitExceededException;
import com.kitapsepeti.cart.repository.CartRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sepetin DB transaction'ları ({@link CartService} bunları Catalog çağrısından SONRA, ayrı ayrı çağırır; Catalog
 * hiçbir zaman transaction içinde çağrılmaz). Her metot entity yerine {@link CartContents} döner.
 * <p>
 * Yazma transaction'ları READ COMMITTED (catalog stok rezervasyonuyla aynı gerekçe): sepeti olmayan kullanıcının
 * {@code FOR UPDATE} okuması REPEATABLE READ'de gap lock alır ve eşzamanlı iki ilk ekleme INSERT'te kilitlenip deadlock
 * olurdu. READ COMMITTED'da kilit alınmaz; ikinci INSERT {@code uk_carts_active_user}'da birincinin commit'ini bekleyip
 * ihlalle düşer ve servis yeniden dener.
 * <p>
 * Flush sırası tuzağı (INSERT → UPDATE → DELETE): buradaki akışlarda aynı transaction'da satır silip aynı kitabı
 * eklemek ya da checkout + yeni sepet YOK. Tekrar ekleme satırı silmez, günceller (adet + anlık görüntü).
 */
@Component
public class CartTransactions {

	private final CartRepository carts;

	private final CartProperties properties;

	private final Clock clock;

	public CartTransactions(CartRepository carts, CartProperties properties, Clock clock) {
		this.carts = carts;
		this.properties = properties;
		this.clock = clock;
	}

	/** Aktif sepet ve satırları kilitsiz, tek sorguda; yoksa boş içerik (sepet oluşturulmaz). */
	@Transactional(readOnly = true)
	public CartContents findActive(UUID userId) {
		return this.carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
			.map(CartContents::of)
			.orElse(CartContents.EMPTY);
	}

	/**
	 * Kitabı kullanıcının aktif sepetine ekler; sepet yoksa açar. Kitap sepetteyse adet artırılır ve anlık görüntü
	 * Catalog'un güncel bilgisiyle yenilenir (kullanıcı tekrar eklerken güncel fiyatı görmüştür).
	 * @param book Catalog'dan bu transaction'dan önce okunmuş, satışta olan kitap
	 * @throws CartLimitExceededException adet ya da satır limiti aşılırsa (sepet değişmez)
	 * @throws org.springframework.dao.DataIntegrityViolationException eşzamanlı ilk sepet ({@code uk_carts_active_user})
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CartContents addItem(UUID userId, CatalogBook book, int quantity) {
		Cart cart = this.carts.findActiveByUserIdForUpdate(userId)
			// Hemen flush: eşzamanlı ilk sepet ihlali satırlar eklenmeden, burada görülsün.
			.orElseGet(() -> this.carts.saveAndFlush(Cart.openFor(userId, this.clock)));
		Optional<CartItem> existing = cart.findItem(book.id());
		if (existing.isPresent()) {
			CartItem item = existing.get();
			int newQuantity = item.getQuantity() + quantity;
			requireQuantityWithinLimit(newQuantity);
			item.changeQuantity(newQuantity, this.clock);
			item.refreshSnapshot(book.priceAmount(), book.currency(), book.title(), book.coverUrl(), this.clock);
		}
		else {
			if (cart.getItems().size() >= this.properties.maxLines()) {
				throw CartLimitExceededException.lines(this.properties.maxLines());
			}
			requireQuantityWithinLimit(quantity);
			cart.addItem(book.id(), quantity, book.priceAmount(), book.currency(), book.title(), book.coverUrl(),
					this.clock);
		}
		this.carts.flush();
		return CartContents.of(cart);
	}

	private void requireQuantityWithinLimit(int quantity) {
		if (quantity > this.properties.maxQuantityPerItem()) {
			throw CartLimitExceededException.quantityPerItem(this.properties.maxQuantityPerItem());
		}
	}

}
