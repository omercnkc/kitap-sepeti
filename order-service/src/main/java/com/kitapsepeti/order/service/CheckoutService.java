package com.kitapsepeti.order.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.order.dto.response.OrderResponse;
import com.kitapsepeti.order.entity.AddressSnapshot;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderLine;
import com.kitapsepeti.order.entity.OrderReasons;
import com.kitapsepeti.order.entity.OrderRuleViolation;
import com.kitapsepeti.order.entity.OrderStatus;
import com.kitapsepeti.order.entity.OrderTotalTooLargeException;
import com.kitapsepeti.order.exception.OrderErrorCode;
import com.kitapsepeti.order.exception.OrderProblemException;
import com.kitapsepeti.order.gateway.BookLookupResult;
import com.kitapsepeti.order.gateway.CartGateway;
import com.kitapsepeti.order.gateway.CartSnapshotResult;
import com.kitapsepeti.order.gateway.CatalogBook;
import com.kitapsepeti.order.gateway.CatalogGateway;
import com.kitapsepeti.order.gateway.NotPerformed;
import com.kitapsepeti.order.gateway.PaymentGateway;
import com.kitapsepeti.order.gateway.PaymentInitiationResult;
import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.ReserveResult;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.gateway.Unavailable;
import com.kitapsepeti.order.gateway.Unknown;
import com.kitapsepeti.order.service.OrderTransactions.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Checkout: sepetten sipariş oluşturur, stoğu ayırır ve ödemeyi başlatır. Bu sınıf transaction açmaz; DB işleri
 * {@link OrderTransactions}'ta ayrı kısa transaction'lar, dış çağrılar (Cart, Catalog, Payment) hepsinin DIŞINDA.
 * <p>
 * Sıra: bekleyen sipariş kontrolü → Cart snapshot → Catalog lookup (fiyat, başlık, satılabilirlik, para birimi) →
 * {@link Order#place} → TX1 kayıt ({@code pending}, {@code requested}) → Catalog reserve → TX2 {@code held} → Payment
 * initiate → TX3 ödeme bağlama. Kayıttan önceki hatalarda sipariş yazılmaz; sonrakilerde sipariş {@code failed} olur
 * (stok bırakma Adım 5'te; o zamana kadar Catalog süresi dolunca kendisi bırakır) ve yanıtta {@code orderId} bulunur.
 * Rezervasyon başarısızsa Payment çağrılmaz.
 * <p>
 * Kayıt sonrası bir geçiş uygulanmazsa (sipariş arada başka bir yolla değişmiş) exception atılmaz: WARN yazılır ve
 * siparişin güncel hali döner. Payment sonucu bilinmiyorsa sipariş {@code pending} + {@code held} kalır ve 201 döner
 * (Adım 8 aynı idempotent istekle yeniden dener).
 * <p>
 * Log: checkout başına tek INFO özet satırı (sonuç kodu + süre); id, tutar, adres ve kitap listesi hiçbir satırda yok.
 */
@Service
public class CheckoutService {

	private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

	/** Özet satırındaki başarı sonucu (201). */
	static final String PLACED = "ORDER_PLACED";

	private static final String PENDING = OrderStatus.PENDING.dbValue();

	private final OrderTransactions transactions;

	private final CartGateway cart;

	private final CatalogGateway catalog;

	private final PaymentGateway payment;

	private final Clock clock;

	public CheckoutService(OrderTransactions transactions, CartGateway cart, CatalogGateway catalog,
			PaymentGateway payment, Clock clock) {
		this.transactions = transactions;
		this.cart = cart;
		this.catalog = catalog;
		this.payment = payment;
		this.clock = clock;
	}

	/**
	 * @return siparişin güncel hali (normalde {@code pending}; kayıt sonrası bir geçiş yarışında başka bir durum)
	 * @throws OrderProblemException iş hatası ya da bağımlı servis kesintisi (kod {@link OrderErrorCode})
	 */
	public OrderResponse checkout(UUID userId, AddressSnapshot address) {
		long start = System.nanoTime();
		String outcome = CommonErrorCode.INTERNAL_ERROR.name();
		try {
			OrderResponse order = place(userId, address);
			outcome = PLACED;
			return order;
		}
		catch (ApiException ex) {
			outcome = ex.getErrorCode().name();
			throw ex;
		}
		finally {
			log.info("Checkout -> {} (durationMs={})", outcome,
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
		}
	}

	private OrderResponse place(UUID userId, AddressSnapshot address) {
		this.transactions.findPendingOrderId(userId).ifPresent(pendingId -> {
			throw new OrderProblemException(OrderErrorCode.ORDER_PENDING_EXISTS, pendingId);
		});
		CartSnapshotResult.Snapshot snapshot = switch (this.cart.snapshot(userId)) {
			case CartSnapshotResult.Snapshot found -> found;
			case CartSnapshotResult.Empty empty -> throw new OrderProblemException(OrderErrorCode.CART_EMPTY);
			case Unavailable unavailable -> throw new OrderProblemException(OrderErrorCode.CART_UNAVAILABLE);
		};
		OrderResponse placed = insert(userId, newOrder(userId, snapshot, address));
		return reserveAndPay(userId, placed, snapshot.lines());
	}

	/** Fiyat ve başlık Catalog'dan, adet Cart'tan. Yanıtta hangi kitabın sorunlu olduğu yazılmaz. */
	private Order newOrder(UUID userId, CartSnapshotResult.Snapshot snapshot, AddressSnapshot address) {
		List<UUID> bookIds = snapshot.lines().stream().map(StockLine::bookId).toList();
		BookLookupResult.Found books = switch (this.catalog.lookup(bookIds)) {
			case BookLookupResult.Found found -> found;
			case Unavailable unavailable -> throw new OrderProblemException(OrderErrorCode.CATALOG_UNAVAILABLE);
		};
		List<OrderLine> lines = new ArrayList<>(snapshot.lines().size());
		Set<String> currencies = new LinkedHashSet<>();
		for (StockLine line : snapshot.lines()) {
			CatalogBook book = books.sellable(line.bookId())
				.orElseThrow(() -> new OrderProblemException(OrderErrorCode.BOOK_NOT_AVAILABLE));
			lines.add(new OrderLine(book.bookId(), book.title(), line.quantity(), book.unitPrice()));
			currencies.add(book.currency());
		}
		if (currencies.size() > 1) {
			throw new OrderProblemException(OrderErrorCode.MIXED_CURRENCY);
		}
		try {
			return Order.place(userId, snapshot.cartId(), currencies.iterator().next(), lines, address, this.clock);
		}
		catch (OrderRuleViolation ex) {
			throw new OrderProblemException(OrderErrorCode.of(ex.code()));
		}
		catch (OrderTotalTooLargeException ex) {
			throw new OrderProblemException(OrderErrorCode.ORDER_TOTAL_TOO_LARGE);
		}
	}

	/** Eşzamanlı checkout'ta kaybeden taraf, kazananın siparişini {@code orderId} olarak alır. */
	private OrderResponse insert(UUID userId, Order order) {
		try {
			return this.transactions.insert(order);
		}
		catch (DataIntegrityViolationException ex) {
			if (!DbConstraints.isViolated(ex, "uk_orders_pending_user")) {
				throw ex;
			}
			throw new OrderProblemException(OrderErrorCode.ORDER_PENDING_EXISTS,
					this.transactions.findPendingOrderId(userId).orElse(null));
		}
	}

	private OrderResponse reserveAndPay(UUID userId, OrderResponse placed, List<StockLine> lines) {
		UUID orderId = placed.id();
		ReserveResult reserve = this.catalog.reserve(orderId, lines);
		if (!(reserve instanceof ReserveResult.Reserved)) {
			return failReservation(orderId, reserve);
		}
		Transition held = this.transactions.markStockHeld(orderId);
		if (!held.applied() || !PENDING.equals(held.order().status())) {
			warnNotApplied("markStockHeld", held);
			return held.order();
		}
		return pay(userId, held.order());
	}

	private OrderResponse failReservation(UUID orderId, ReserveResult reserve) {
		return switch (reserve) {
			case ReserveResult.Insufficient insufficient ->
				fail(orderId, OrderReasons.OUT_OF_STOCK, OrderErrorCode.INSUFFICIENT_STOCK);
			case ReserveResult.NotSellable notSellable ->
				fail(orderId, OrderReasons.BOOK_NOT_AVAILABLE, OrderErrorCode.BOOK_NOT_AVAILABLE);
			case ReserveResult.NotHeld notHeld -> {
				// Yeni siparişin rezervasyonu committed/released olamaz: bizim taraftaki bir hata.
				log.error("Checkout reserve returned a reservation that is not held (status={})", notHeld.status());
				yield fail(orderId, OrderReasons.CATALOG_UNAVAILABLE, OrderErrorCode.CATALOG_UNAVAILABLE);
			}
			case Rejected rejected -> {
				log.error("Checkout reserve rejected by catalog (status={}, code={})", rejected.httpStatus(),
						rejected.code());
				yield fail(orderId, OrderReasons.CATALOG_UNAVAILABLE, OrderErrorCode.CATALOG_UNAVAILABLE);
			}
			case NotPerformed notPerformed ->
				fail(orderId, OrderReasons.CATALOG_UNAVAILABLE, OrderErrorCode.CATALOG_UNAVAILABLE);
			case Unknown unknown -> fail(orderId, OrderReasons.CATALOG_UNAVAILABLE, OrderErrorCode.CATALOG_UNAVAILABLE);
			case ReserveResult.Reserved reserved -> throw new IllegalStateException("Reservation succeeded");
		};
	}

	/** Initiated'daki ödeme durumu (succeeded/failed) burada kullanılmaz; sonuç ödeme olayından gelir (Adım 6). */
	private OrderResponse pay(UUID userId, OrderResponse held) {
		UUID orderId = held.id();
		return switch (this.payment.initiate(orderId, userId, held.totalAmount(), held.currency())) {
			case PaymentInitiationResult.Initiated initiated -> {
				Transition attached = this.transactions.attachPayment(orderId, initiated.paymentId());
				if (!attached.applied()) {
					warnNotApplied("attachPayment", attached);
				}
				yield attached.order();
			}
			case Unknown unknown -> {
				log.warn("Checkout payment outcome is unknown; order stays pending with stock held");
				yield held;
			}
			case NotPerformed notPerformed ->
				fail(orderId, OrderReasons.PAYMENT_UNAVAILABLE, OrderErrorCode.PAYMENT_UNAVAILABLE);
			case Rejected rejected -> {
				log.error("Checkout payment rejected by payment service (status={}, code={})", rejected.httpStatus(),
						rejected.code());
				yield fail(orderId, OrderReasons.PAYMENT_REJECTED, OrderErrorCode.PAYMENT_UNAVAILABLE);
			}
		};
	}

	/**
	 * Siparişi {@code failed} yapar ve hatayı {@code orderId} ile fırlatır. Sipariş arada başka bir yolla değiştiyse
	 * (geçiş uygulanmadı) hata yerine güncel hali döner.
	 */
	private OrderResponse fail(UUID orderId, String failureCode, ErrorCode error) {
		Transition failed = this.transactions.markFailed(orderId, failureCode);
		if (!failed.applied()) {
			warnNotApplied("markFailed", failed);
			return failed.order();
		}
		throw new OrderProblemException(error, orderId);
	}

	private static void warnNotApplied(String step, Transition transition) {
		log.warn("Checkout {} was not applied (result={}, status={}); returning current order", step,
				transition.result(), transition.order().status());
	}

}
