package com.kitapsepeti.cart.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Sepet ({@code carts} tablosu), satırlarıyla birlikte aggregate root. Satırlar yalnızca bu sınıf üzerinden
 * eklenir/silinir; listeden çıkan satır flush'ta silinir (orphanRemoval).
 * <p>
 * Burada yalnızca bütünlük kuralları var (aynı kitap tek satır, durum geçişleri, geçmiş sepet değişmez);
 * satır/adet limitleri servis katmanında ({@code app.cart.*}).
 * <p>
 * {@code active_user_id} generated kolonu bilerek eşlenmez: DB hesaplar, uygulama okumaz.
 */
@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {

	/** Uygulama tarafında üretilen, zamana göre sıralı UUID v7; DB'de BINARY(16). */
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** user-service'teki kullanıcı (JWT {@code sub}); servisler arası olduğu için FK yok. */
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** DB'de küçük harf ('active'/'checked_out'/'abandoned'); varsayılan Java'da da verilir, DB default'una güvenilmez. */
	@Convert(converter = CartStatusConverter.class)
	@Column(name = "status", nullable = false, length = 16)
	private CartStatus status = CartStatus.ACTIVE;

	/** {@link #openFor} saatinden; Hibernate INSERT'i kolonu açıkça yazdığı için DB default'u devreye girmez. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** İlk değer {@link #openFor} saatinden; sonraki her UPDATE'te {@link #onUpdate()} yeniler. */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("addedAt ASC, id ASC")
	private List<CartItem> items = new ArrayList<>();

	/** Kullanıcı için yeni, aktif ve boş sepet. */
	public static Cart openFor(UUID userId, Clock clock) {
		Cart cart = new Cart();
		cart.userId = Objects.requireNonNull(userId, "userId");
		cart.createdAt = now(clock);
		cart.updatedAt = cart.createdAt;
		return cart;
	}

	/** Satırlar eklenme sırasıyla; değiştirmek için bu sınıfın metotları kullanılır. */
	public List<CartItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	/**
	 * Kitabı yeni satır olarak ekler. Kitap zaten sepetteyse {@link IllegalStateException}; o durumda mevcut
	 * satırın adedi/anlık görüntüsü güncellenir ({@link CartItem#changeQuantity}, {@link CartItem#refreshSnapshot}).
	 * {@code currency} null ise {@value CartItem#DEFAULT_CURRENCY}.
	 */
	public CartItem addItem(UUID bookId, int quantity, BigDecimal unitPrice, String currency, String title,
			String coverUrl, Clock clock) {
		requireActive();
		Objects.requireNonNull(bookId, "bookId");
		if (findItem(bookId).isPresent()) {
			throw new IllegalStateException("Book is already in the cart: " + bookId);
		}
		CartItem item = new CartItem(this, bookId, quantity, unitPrice, currency, title, coverUrl, now(clock));
		items.add(item);
		return item;
	}

	public Optional<CartItem> findItem(UUID bookId) {
		return items.stream().filter(item -> item.getBookId().equals(bookId)).findFirst();
	}

	/** Henüz flush edilmemiş satırın id'si yoktur; o satır id ile bulunamaz. */
	public Optional<CartItem> findItemById(UUID itemId) {
		return items.stream().filter(item -> itemId.equals(item.getId())).findFirst();
	}

	/** Satırı sepetten çıkarır (flush'ta silinir). Satır bu sepette yoksa false. */
	public boolean removeItem(UUID itemId) {
		requireActive();
		return findItemById(itemId).map(items::remove).orElse(false);
	}

	/** Tüm satırları çıkarır; sepetin kendisi kalır. */
	public void clear() {
		requireActive();
		items.clear();
	}

	/** {@code ACTIVE} → {@code CHECKED_OUT}; başka durumdan {@link IllegalStateException}. */
	public void checkout() {
		transitionTo(CartStatus.CHECKED_OUT);
	}

	/** {@code ACTIVE} → {@code ABANDONED}; başka durumdan {@link IllegalStateException}. */
	public void abandon() {
		transitionTo(CartStatus.ABANDONED);
	}

	/** Geçmiş (checked_out/abandoned) sepetin satırları değişmez. */
	void requireActive() {
		if (status != CartStatus.ACTIVE) {
			throw new IllegalStateException("Cart is not active: " + status);
		}
	}

	private void transitionTo(CartStatus target) {
		if (status != CartStatus.ACTIVE) {
			throw new IllegalStateException("Cart cannot move from " + status + " to " + target);
		}
		status = target;
	}

	/**
	 * Hibernate {@code @CurrentTimestamp(event = UPDATE)} kolonu INSERT'ten çıkarıyor (DB default'u yazıyor), bu yüzden
	 * güncelleme anı callback'le verilir. Uygulama saati entity'ye ulaşmadığı için sistem saati (UTC Instant).
	 */
	@PreUpdate
	void onUpdate() {
		updatedAt = now(Clock.systemUTC());
	}

	/** DATETIME(6) ile aynı hassasiyet: kaydedilen ve bellekteki değer eşit kalır. */
	static Instant now(Clock clock) {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

}
