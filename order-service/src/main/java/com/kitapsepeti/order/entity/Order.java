package com.kitapsepeti.order.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.kitapsepeti.order.entity.OrderRuleViolation.Code;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Sipariş ({@code orders}), kalemleri ve durum geçmişiyle birlikte aggregate root.
 * <p>
 * Setter yok: durum, stok durumu, ödeme ve hata kodu yalnızca domain metotlarıyla değişir. Metotlar V1'deki CHECK'lerin
 * (tutarlar, "failed ⇔ failure_code", "paid ⇒ payment_id", durum/stok tutarlılığı) Java karşılığını uygular; JPA yolundan
 * CHECK ihlali çıkmaz. Geçişler {@link TransitionResult} döner (payment kalıbı); geçişin denenmemesi gereken durumlar
 * {@link IllegalStateException}. Her uygulanan geçiş {@code updatedAt}'i yeniler; yalnızca durum geçişleri geçmiş yazar.
 * <p>
 * {@code active_pending_user_id} generated kolonu bilerek eşlenmez: DB hesaplar ({@code uk_orders_pending_user}).
 * Kalem ve geçmiş satırları cascade ile yazılır, silinmez (orphanRemoval yok; FK RESTRICT).
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

	public static final int MIN_QUANTITY = 1;

	/** {@code ck_order_items_quantity} = cart'ın DB üst sınırı; iş limiti cart'ta. */
	public static final int MAX_QUANTITY = 99;

	/** catalog {@code books.title} ile aynı. */
	public static final int TITLE_MAX_LENGTH = 300;

	/** DECIMAL(12,2) üst sınırı. */
	public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

	private static final int AMOUNT_SCALE = 2;

	private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

	private static final Pattern REASON_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

	/** Uygulama tarafında üretilen, zamana göre sıralı UUID v7; DB'de BINARY(16). */
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** user-service'teki kullanıcı (JWT {@code sub}); servisler arası olduğu için FK yok. */
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** cart-service'teki sepet; yalnızca iz için. */
	@Column(name = "cart_id", nullable = false, updatable = false)
	private UUID cartId;

	/** DB'de küçük harf; Java'da da başlangıç değeri verilir, DB default'una güvenilmez. */
	@Convert(converter = OrderStatusConverter.class)
	@Column(name = "status", nullable = false, length = 16)
	private OrderStatus status = OrderStatus.PENDING;

	@Convert(converter = StockStateConverter.class)
	@Column(name = "stock_state", nullable = false, length = 16)
	private StockState stockState = StockState.REQUESTED;

	/** ISO 4217, büyük harf ({@code ck_orders_currency}). */
	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	/** Kalemlerin {@code lineTotal} toplamı; scale 2, pozitif. */
	@Column(name = "subtotal", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	/** v1'de kupon yok: her zaman 0.00 (Java'da açıkça yazılır). */
	@Column(name = "discount_amount", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal discountAmount;

	/** {@code subtotal - discountAmount}; Payment'a giden tutar. */
	@Column(name = "total_amount", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal totalAmount;

	/** v1'de kupon yok: her zaman null. */
	@Column(name = "coupon_code", updatable = false, length = 40)
	private String couponCode;

	/** JSON nesnesi; eşleme {@link AddressSnapshotConverter}'da (sabit alan adları, bilinmeyen alan hata). */
	@Convert(converter = AddressSnapshotConverter.class)
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "address_snapshot", nullable = false, updatable = false)
	private AddressSnapshot addressSnapshot;

	/** payment-service'teki ödeme; {@link #attachPayment} ya da {@link #markPaid} ile bir kez yazılır. */
	@Column(name = "payment_id")
	private UUID paymentId;

	/** Yalnızca {@link OrderStatus#FAILED} siparişte dolu ({@code ck_orders_failure}). */
	@Column(name = "failure_code", length = 64)
	private String failureCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Sıra: id (UUID v7, eklenme sırası). */
	@OneToMany(mappedBy = "order", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@OrderBy("id ASC")
	private List<OrderItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "order", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@OrderBy("createdAt ASC, id ASC")
	private List<OrderStatusHistory> history = new ArrayList<>();

	/**
	 * Yeni, {@code pending} sipariş: stok {@code requested}, ödeme ve hata kodu yok, indirim 0, toplam = ara toplam.
	 * İlk geçmiş satırı {@code null → pending} ({@link OrderReasons#ORDER_PLACED}).
	 *
	 * @param lines boş olamaz; her kitap bir kez, adet {@value #MIN_QUANTITY}..{@value #MAX_QUANTITY}, birim fiyat
	 *        negatif değil ve en fazla 2 ondalık (10.001 yuvarlanmaz, reddedilir)
	 * @throws OrderRuleViolation iş kuralı ihlalinde (kod: {@link Code})
	 * @throws OrderTotalTooLargeException toplam DECIMAL(12,2)'yi aşıyorsa
	 * @throws IllegalArgumentException başlık boş/uzunsa
	 */
	public static Order place(UUID userId, UUID cartId, String currency, List<OrderLine> lines,
			AddressSnapshot address, Clock clock) {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(cartId, "cartId");
		Objects.requireNonNull(lines, "lines");
		Objects.requireNonNull(address, "address");
		if (currency == null || !CURRENCY.matcher(currency).matches()) {
			throw new OrderRuleViolation(Code.INVALID_CURRENCY);
		}
		if (lines.isEmpty()) {
			throw new OrderRuleViolation(Code.EMPTY_ORDER);
		}
		Set<UUID> books = new HashSet<>();
		for (OrderLine line : lines) {
			Objects.requireNonNull(line, "line");
			if (!books.add(Objects.requireNonNull(line.bookId(), "bookId"))) {
				throw new OrderRuleViolation(Code.DUPLICATE_BOOK);
			}
		}

		Order order = new Order();
		BigDecimal subtotal = BigDecimal.ZERO.setScale(AMOUNT_SCALE);
		for (OrderLine line : lines) {
			OrderItem item = new OrderItem(order, line.bookId(), validTitle(line.title()), validQuantity(line.quantity()),
					validPrice(line.unitPrice()));
			order.items.add(item);
			subtotal = subtotal.add(item.getLineTotal());
		}
		if (subtotal.signum() == 0) {
			throw new OrderRuleViolation(Code.ORDER_TOTAL_ZERO);
		}
		if (subtotal.compareTo(MAX_AMOUNT) > 0) {
			throw new OrderTotalTooLargeException();
		}

		order.userId = userId;
		order.cartId = cartId;
		order.currency = currency;
		order.subtotal = subtotal;
		order.discountAmount = BigDecimal.ZERO.setScale(AMOUNT_SCALE);
		order.totalAmount = subtotal;
		order.addressSnapshot = address;
		order.createdAt = now(clock);
		order.updatedAt = order.createdAt;
		order.history.add(new OrderStatusHistory(order, null, OrderStatus.PENDING, OrderReasons.ORDER_PLACED,
				order.createdAt));
		return order;
	}

	/** Kalemler eklenme sırasıyla; değişmez. */
	public List<OrderItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	/** Geçmiş satırları zaman sırasıyla; değişmez. */
	public List<OrderStatusHistory> getHistory() {
		return Collections.unmodifiableList(history);
	}

	/** Catalog rezervasyonu tuttu: {@code requested → held}. Son stok durumları ({@code committed}, {@code released}) çelişir. */
	public TransitionResult markStockHeld(Clock clock) {
		return switch (stockState) {
			case REQUESTED -> {
				stockState = StockState.HELD;
				touch(clock);
				yield TransitionResult.APPLIED;
			}
			case HELD -> TransitionResult.ALREADY_IN_STATE;
			case COMMITTED, RELEASED -> TransitionResult.CONFLICTING_FINAL;
		};
	}

	/** Payment'ta oluşturulan ödemeyi bağlar. Aynı id tekrar → değişiklik yok; başka id → çelişki (değer değişmez). */
	public TransitionResult attachPayment(UUID paymentId, Clock clock) {
		Objects.requireNonNull(paymentId, "paymentId");
		if (this.paymentId == null) {
			this.paymentId = paymentId;
			touch(clock);
			return TransitionResult.APPLIED;
		}
		return this.paymentId.equals(paymentId) ? TransitionResult.ALREADY_IN_STATE : TransitionResult.CONFLICTING_FINAL;
	}

	/**
	 * Ödeme başarılı: {@code pending → paid} (stok {@code held} olmalı). Ödeme henüz bağlı değilse bağlanır; başka bir
	 * ödeme bağlıysa çelişki. Başarısız siparişe geç gelen başarı çelişkidir (telafi Adım 8).
	 *
	 * @throws IllegalStateException sipariş bekliyor ama stok henüz tutulmamışsa (ödeme rezervasyonsuz başlatılmaz)
	 */
	public TransitionResult markPaid(UUID paymentId, Clock clock) {
		Objects.requireNonNull(paymentId, "paymentId");
		return switch (status) {
			case PENDING -> {
				if (stockState != StockState.HELD) {
					throw new IllegalStateException("Order cannot be paid while stock is " + stockState.dbValue());
				}
				if (this.paymentId != null && !this.paymentId.equals(paymentId)) {
					yield TransitionResult.CONFLICTING_FINAL;
				}
				this.paymentId = paymentId;
				changeStatus(OrderStatus.PAID, OrderReasons.PAYMENT_SUCCEEDED, clock);
				yield TransitionResult.APPLIED;
			}
			case PAID -> this.paymentId.equals(paymentId) ? TransitionResult.ALREADY_IN_STATE
					: TransitionResult.CONFLICTING_FINAL;
			case FAILED -> TransitionResult.CONFLICTING_FINAL;
		};
	}

	/**
	 * {@code pending → failed}; geçmiş satırının reason'ı failure code. Zaten failed ise ilk kod korunur
	 * ({@link TransitionResult#ALREADY_IN_STATE}, kod farklı olsa da); paid ise çelişki.
	 *
	 * @param failureCode {@code ^[A-Z][A-Z0-9_]*$}, en fazla 64 karakter (ör. {@link OrderReasons#ORDER_EXPIRED})
	 * @throws IllegalArgumentException kod biçimi geçersizse (durumdan bağımsız; programlama hatası)
	 */
	public TransitionResult markFailed(String failureCode, Clock clock) {
		String code = validReasonCode(failureCode);
		return switch (status) {
			case PENDING -> {
				this.failureCode = code;
				changeStatus(OrderStatus.FAILED, code, clock);
				yield TransitionResult.APPLIED;
			}
			case FAILED -> TransitionResult.ALREADY_IN_STATE;
			case PAID -> TransitionResult.CONFLICTING_FINAL;
		};
	}

	/** Ödenmiş siparişin stoğu kesinleşti: {@code held → committed}. Sipariş ödenmemişse ya da stok bırakılmışsa çelişki. */
	public TransitionResult markStockCommitted(Clock clock) {
		if (status != OrderStatus.PAID) {
			return TransitionResult.CONFLICTING_FINAL;
		}
		return switch (stockState) {
			case HELD -> {
				stockState = StockState.COMMITTED;
				touch(clock);
				yield TransitionResult.APPLIED;
			}
			case COMMITTED -> TransitionResult.ALREADY_IN_STATE;
			case RELEASED -> TransitionResult.CONFLICTING_FINAL;
			case REQUESTED -> throw impossibleState();
		};
	}

	/**
	 * Başarısız siparişin stoğu bırakıldı: {@code requested|held → released}. Sipariş başarısız değilse (bekliyor ya da
	 * ödenmiş) ya da stok kesinleşmişse çelişki.
	 */
	public TransitionResult markStockReleased(Clock clock) {
		if (status != OrderStatus.FAILED) {
			return TransitionResult.CONFLICTING_FINAL;
		}
		return switch (stockState) {
			case REQUESTED, HELD -> {
				stockState = StockState.RELEASED;
				touch(clock);
				yield TransitionResult.APPLIED;
			}
			case RELEASED -> TransitionResult.ALREADY_IN_STATE;
			case COMMITTED -> TransitionResult.CONFLICTING_FINAL;
		};
	}

	private void changeStatus(OrderStatus target, String reason, Clock clock) {
		OrderStatus from = status;
		status = target;
		touch(clock);
		history.add(new OrderStatusHistory(this, from, target, reason, updatedAt));
	}

	private void touch(Clock clock) {
		updatedAt = now(clock);
	}

	/** DB CHECK'lerinin dışladığı birleşim (ör. paid + requested); ancak veri elle bozulduysa görülür. */
	private IllegalStateException impossibleState() {
		return new IllegalStateException(
				"Order is in an impossible state: " + status.dbValue() + " / " + stockState.dbValue());
	}

	/** DATETIME(6) ile aynı hassasiyet: kaydedilen ve bellekteki değer eşit kalır. */
	static Instant now(Clock clock) {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static String validTitle(String title) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Order line title must not be blank");
		}
		if (title.length() > TITLE_MAX_LENGTH) {
			throw new IllegalArgumentException("Order line title must be at most " + TITLE_MAX_LENGTH + " characters");
		}
		return title;
	}

	private static int validQuantity(int quantity) {
		if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
			throw new OrderRuleViolation(Code.INVALID_QUANTITY);
		}
		return quantity;
	}

	/** 149.9 → 149.90; 149.999 yuvarlanmaz, reddedilir. */
	private static BigDecimal validPrice(BigDecimal unitPrice) {
		Objects.requireNonNull(unitPrice, "unitPrice");
		if (unitPrice.signum() < 0 || unitPrice.compareTo(MAX_AMOUNT) > 0) {
			throw new OrderRuleViolation(Code.INVALID_PRICE);
		}
		try {
			return unitPrice.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw new OrderRuleViolation(Code.INVALID_PRICE);
		}
	}

	private static String validReasonCode(String code) {
		if (code == null || !REASON_CODE.matcher(code).matches()) {
			throw new IllegalArgumentException("Failure code must match " + REASON_CODE.pattern());
		}
		return code;
	}

}
