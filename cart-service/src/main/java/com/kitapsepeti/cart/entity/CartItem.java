package com.kitapsepeti.cart.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Sepet satırı ({@code cart_items} tablosu). Yalnızca {@link Cart#addItem} ile oluşur; sepet başına kitap başına
 * tek satır ({@code uk_cart_items_cart_book}). {@code *Snapshot} alanları kitabın sepete eklendiği (veya en son
 * yenilendiği) andaki Catalog bilgisidir.
 */
@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

	/** {@code ck_cart_items_quantity} ile aynı sınırlar; iş limiti ({@code app.cart.max-quantity-per-item}) serviste. */
	public static final int MIN_QUANTITY = 1;

	public static final int MAX_QUANTITY = 99;

	public static final String DEFAULT_CURRENCY = "TRY";

	private static final int PRICE_SCALE = 2;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cart_id", nullable = false, updatable = false)
	private Cart cart;

	/** catalog-service'teki kitap; servisler arası olduğu için FK yok. */
	@Column(name = "book_id", nullable = false, updatable = false)
	private UUID bookId;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	/** Her zaman scale 2 (DECIMAL(12,2)); negatif olamaz ({@code ck_cart_items_price}). */
	@Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPriceSnapshot;

	/** ISO 4217 para birimi kodu (CHAR(3)); varsayılan Java'da da verilir. */
	@Column(name = "currency_snapshot", nullable = false, length = 3)
	private String currencySnapshot = DEFAULT_CURRENCY;

	@Column(name = "title_snapshot", nullable = false, length = 300)
	private String titleSnapshot;

	@Column(name = "cover_url_snapshot", length = 500)
	private String coverUrlSnapshot;

	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	/** İlk değer eklenme anı; satırı değiştiren her metot verilen saatle yeniler (sepetinkini de). */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	CartItem(Cart cart, UUID bookId, int quantity, BigDecimal unitPrice, String currency, String title,
			String coverUrl, Instant addedAt) {
		this.cart = cart;
		this.bookId = bookId;
		this.quantity = validQuantity(quantity);
		applySnapshot(unitPrice, currency, title, coverUrl);
		this.addedAt = addedAt;
		this.updatedAt = addedAt;
	}

	/** {@value #MIN_QUANTITY}..{@value #MAX_QUANTITY} dışında {@link IllegalArgumentException} (hiçbir şey değişmez). */
	public void changeQuantity(int quantity, Clock clock) {
		cart.requireActive();
		this.quantity = validQuantity(quantity);
		touch(clock);
	}

	/** Catalog'dan okunan güncel bilgiyle anlık görüntüyü değiştirir. {@code currency} null ise {@value #DEFAULT_CURRENCY}. */
	public void refreshSnapshot(BigDecimal unitPrice, String currency, String title, String coverUrl, Clock clock) {
		cart.requireActive();
		applySnapshot(unitPrice, currency, title, coverUrl);
		touch(clock);
	}

	private void touch(Clock clock) {
		this.updatedAt = Cart.now(clock);
		cart.touch(clock);
	}

	private void applySnapshot(BigDecimal unitPrice, String currency, String title, String coverUrl) {
		this.unitPriceSnapshot = normalizePrice(unitPrice);
		this.currencySnapshot = (currency != null) ? currency : DEFAULT_CURRENCY;
		this.titleSnapshot = Objects.requireNonNull(title, "title");
		this.coverUrlSnapshot = coverUrl;
	}

	private static int validQuantity(int quantity) {
		if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
			throw new IllegalArgumentException(
					"Quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY + ": " + quantity);
		}
		return quantity;
	}

	/** 149.9 → 149.90; 149.999 yuvarlanmaz, reddedilir. */
	private static BigDecimal normalizePrice(BigDecimal unitPrice) {
		Objects.requireNonNull(unitPrice, "unitPrice");
		if (unitPrice.signum() < 0) {
			throw new IllegalArgumentException("Unit price must not be negative");
		}
		try {
			return unitPrice.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw new IllegalArgumentException("Unit price must have at most " + PRICE_SCALE + " decimal places", ex);
		}
	}

}
