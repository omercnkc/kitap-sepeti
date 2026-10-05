package com.kitapsepeti.order.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.kitapsepeti.common.outbox.OutboxService;
import com.kitapsepeti.order.dto.response.OrderResponse;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderStatus;
import com.kitapsepeti.order.entity.StockState;
import com.kitapsepeti.order.entity.TransitionResult;
import com.kitapsepeti.order.repository.OrderRepository;
import com.kitapsepeti.order.service.PermanentPaymentResultException.Reason;
import com.kitapsepeti.order.service.event.CartCheckedOutEvent;
import com.kitapsepeti.order.service.event.OrderFailedEvent;
import com.kitapsepeti.order.service.event.OrderPaidEvent;
import com.kitapsepeti.order.service.event.StockCommitReadyEvent;
import com.kitapsepeti.order.service.event.StockReleaseReadyEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siparişin DB transaction'ları. Servisler bunları dış çağrılardan (Cart, Catalog, Payment) önce ya da sonra ayrı ayrı
 * çağırır; dış çağrı hiçbir zaman transaction içinde yapılmaz. Her metot entity yerine {@link OrderResponse} döner
 * (kalemler transaction içinde yüklenir).
 * <p>
 * Yazma transaction'ları READ COMMITTED (cart ve catalog ile aynı gerekçe): eşzamanlı iki checkout'un ikinci INSERT'i
 * {@code uk_orders_pending_user}'da birincinin commit'ini bekleyip ihlalle düşer; gap lock / deadlock yok. Geçişler
 * siparişi {@code FOR UPDATE} ile okur: aynı siparişe eşzamanlı yollar (ileride ödeme sonucu, zaman aşımı) sıraya girer.
 */
@Component
public class OrderTransactions {

	private final OrderRepository orders;

	private final OutboxService outbox;

	private final OrderEventFactory events;

	private final Clock clock;

	private final org.springframework.context.ApplicationEventPublisher eventPublisher;

	public OrderTransactions(OrderRepository orders, OutboxService outbox, OrderEventFactory events, Clock clock) {
		this(orders, outbox, events, clock, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public OrderTransactions(OrderRepository orders, OutboxService outbox, OrderEventFactory events, Clock clock,
			org.springframework.context.ApplicationEventPublisher eventPublisher) {
		this.orders = orders;
		this.outbox = outbox;
		this.events = events;
		this.clock = clock;
		this.eventPublisher = eventPublisher;
	}

	/** Geçişin sonucu ve siparişin geçişten sonraki (uygulanmadıysa mevcut) hali. */
	public record Transition(TransitionResult result, OrderResponse order) {

		public boolean applied() {
			return this.result == TransitionResult.APPLIED;
		}

	}

	public enum PaymentOutcome {
		APPLIED,
		ALREADY_IN_STATE,
		/** Failed siparişe başarılı ödeme: {@code late_payment_at} yazıldı, sipariş failed kaldı. */
		LATE_PAYMENT_SUCCESS,
		/** {@link #LATE_PAYMENT_SUCCESS} ama siparişte başka bir ödeme bağlı (bağlı ödeme değişmedi). */
		LATE_PAYMENT_ID_CONFLICT,
		PAYMENT_ID_CONFLICT,
		CONFLICTING_FINAL
	}

	public record PaymentTransition(PaymentOutcome outcome, OrderResponse order) {
	}

	/** Kullanıcının bekleyen siparişinin id'si (en fazla bir tane); kilitsiz. */
	@Transactional(readOnly = true)
	public Optional<UUID> findPendingOrderId(UUID userId) {
		return this.orders.findPendingByUserId(userId).map(Order::getId);
	}

	/** Sahibinin siparişi, kalemleriyle; başka kullanıcının siparişi de olmayan sipariş de boş. Kilitsiz. */
	@Transactional(readOnly = true)
	public Optional<OrderResponse> findOwned(UUID orderId, UUID userId) {
		return this.orders.findByIdAndUserId(orderId, userId).map(OrderResponse::of);
	}

	/**
	 * Yeni siparişi kalemleri ve ilk geçmiş satırıyla yazar ({@code pending}, {@code requested}).
	 *
	 * @throws org.springframework.dao.DataIntegrityViolationException kullanıcının zaten bekleyen siparişi varsa
	 *         ({@code uk_orders_pending_user}; eşzamanlı checkout)
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public OrderResponse insert(Order order) {
		return OrderResponse.of(this.orders.saveAndFlush(order));
	}

	/** Catalog rezervasyonu tuttu: {@code requested → held}. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition markStockHeld(UUID orderId) {
		return transition(orderId, order -> order.markStockHeld(this.clock));
	}

	/** Catalog rezervasyonu kesinleşti: {@code held → committed}. Yalnızca sipariş paid ise uygulanır. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition markStockCommitted(UUID orderId) {
		return transition(orderId, order -> order.markStockCommitted(this.clock));
	}

	/**
	 * Catalog rezervasyonu bırakılmış veya bulunamayan siparişin stoğu kaybedildi: {@code held → lost}.
	 * Yalnızca sipariş paid ise uygulanır; tekrar deneme yok, ERROR STOCK_COMMIT_LOST loglanır.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition markStockLost(UUID orderId) {
		return transition(orderId, order -> order.markStockLost(this.clock));
	}

	/** Catalog rezervasyonu bırakıldı: {@code requested/held → released}. Yalnızca sipariş failed ise uygulanır. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition markStockReleased(UUID orderId) {
		return transition(orderId, order -> order.markStockReleased(this.clock));
	}

	/**
	 * {@code pending → failed}; stok durumu korunur. APPLIED ise aynı transaction'da {@code OrderFailed}
	 * outbox satırı yazılır. Böylece checkout dahil failed olan her sipariş tek bir başarısızlık olayı üretir.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition markFailed(UUID orderId, String failureCode) {
		Order order = lock(orderId);
		TransitionResult result = order.markFailed(failureCode, this.clock);
		if (result == TransitionResult.APPLIED) {
			appendFailed(order);
		}
		this.orders.flush();
		return new Transition(result, OrderResponse.of(order));
	}

	/** Payment'ta oluşturulan ödemeyi siparişe bağlar. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition attachPayment(UUID orderId, UUID paymentId) {
		return transition(orderId, order -> order.attachPayment(paymentId, this.clock));
	}

	/**
	 * PaymentSucceeded: tutar/para birimi doğrulaması, durum geçişi ve iki outbox olayı aynı transaction'dadır.
	 *
	 * @throws PermanentPaymentResultException sipariş yoksa veya ödeme bilgileri siparişle uyuşmuyorsa
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PaymentTransition applyPaymentSucceeded(UUID orderId, UUID paymentId, BigDecimal amount, String currency) {
		Order order = lockPaymentResult(orderId);
		validatePayment(order, amount, currency);
		return succeed(order, paymentId);
	}

	/**
	 * Uzlaştırma görevinde Payment'ın "succeeded" yanıtı: {@link #applyPaymentSucceeded} ile aynı geçiş ve olaylar. Tutar
	 * doğrulaması yok (Payment ödemeyi siparişin kendi tutarıyla, orderId'ye idempotent oluşturur).
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PaymentTransition reconcilePaymentSucceeded(UUID orderId, UUID paymentId) {
		return succeed(lock(orderId), paymentId);
	}

	/**
	 * PaymentFailed: tutar/para birimi doğrulaması, durum geçişi ve {@code OrderFailed} aynı transaction'dadır.
	 * Başarısız ödemede CartCheckedOut üretilmez; sepet aktif kalır.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PaymentTransition applyPaymentFailed(UUID orderId, UUID paymentId, BigDecimal amount, String currency,
			String failureCode) {
		Order order = lockPaymentResult(orderId);
		validatePayment(order, amount, currency);
		return fail(order, paymentId, failureCode);
	}

	/** Uzlaştırma görevinde Payment'ın "failed" yanıtı: {@link #applyPaymentFailed} ile aynı geçiş ve olaylar. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PaymentTransition reconcilePaymentFailed(UUID orderId, UUID paymentId, String failureCode) {
		return fail(lock(orderId), paymentId, failureCode);
	}

	/**
	 * Uzlaştırma görevi: bekleyen siparişi {@code failed} yapar ({@code OrderFailed} aynı TX'te) ve commit'ten sonra stok
	 * release'i dispatcher'a bırakır. Kilit altında sipariş hâlâ pending ama stok durumu seçimdekinden farklıysa (checkout
	 * arada ilerledi) dokunulmaz: {@link TransitionResult#CONFLICTING_FINAL}, sonraki tur yeniden değerlendirir.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition failPendingAndReleaseStock(UUID orderId, StockState expectedStock, String failureCode) {
		Order order = lock(orderId);
		if (order.getStatus() == OrderStatus.PENDING && order.getStockState() != expectedStock) {
			return new Transition(TransitionResult.CONFLICTING_FINAL, OrderResponse.of(order));
		}
		TransitionResult result = order.markFailed(failureCode, this.clock);
		if (result == TransitionResult.APPLIED) {
			appendFailed(order);
			publishEvent(new StockReleaseReadyEvent(order.getId()));
		}
		this.orders.flush();
		return new Transition(result, OrderResponse.of(order));
	}

	/**
	 * Başarılı ödeme: pending → paid + {@code OrderPaid} + {@code CartCheckedOut} + commit sonrası stok commit. Failed
	 * siparişte geç ödeme kaydı ({@link Order#recordLatePayment}); diğer sipariş başka bir ödemeye bağlıysa çelişki.
	 */
	private PaymentTransition succeed(Order order, UUID paymentId) {
		OrderStatus before = order.getStatus();
		if (before != OrderStatus.FAILED && order.getPaymentId() != null && !order.getPaymentId().equals(paymentId)) {
			return paymentTransition(PaymentOutcome.PAYMENT_ID_CONFLICT, order);
		}
		TransitionResult result = order.markPaid(paymentId, this.clock);
		PaymentOutcome outcome = switch (result) {
			case APPLIED -> PaymentOutcome.APPLIED;
			case ALREADY_IN_STATE -> PaymentOutcome.ALREADY_IN_STATE;
			case CONFLICTING_FINAL -> before == OrderStatus.FAILED ? recordLatePayment(order, paymentId)
					: PaymentOutcome.CONFLICTING_FINAL;
		};
		if (result == TransitionResult.APPLIED) {
			this.outbox.append(OrderEventFactory.ORDER_AGGREGATE, order.getId(), OrderPaidEvent.TYPE,
					eventId -> this.events.paid(eventId, order));
			this.outbox.append(OrderEventFactory.ORDER_AGGREGATE, order.getId(), CartCheckedOutEvent.TYPE,
					eventId -> this.events.cartCheckedOut(eventId, order));
			publishEvent(new StockCommitReadyEvent(order.getId()));
		}
		this.orders.flush();
		return paymentTransition(outcome, order);
	}

	private PaymentOutcome recordLatePayment(Order order, UUID paymentId) {
		boolean otherPayment = order.getPaymentId() != null && !order.getPaymentId().equals(paymentId);
		return switch (order.recordLatePayment(paymentId, this.clock)) {
			case APPLIED -> otherPayment ? PaymentOutcome.LATE_PAYMENT_ID_CONFLICT : PaymentOutcome.LATE_PAYMENT_SUCCESS;
			case ALREADY_IN_STATE -> PaymentOutcome.ALREADY_IN_STATE;
			case CONFLICTING_FINAL -> PaymentOutcome.CONFLICTING_FINAL;
		};
	}

	/** Başarısız ödeme: pending → failed + {@code OrderFailed} + commit sonrası stok release. */
	private PaymentTransition fail(Order order, UUID paymentId, String failureCode) {
		if (order.getPaymentId() != null && !order.getPaymentId().equals(paymentId)) {
			return paymentTransition(PaymentOutcome.PAYMENT_ID_CONFLICT, order);
		}
		TransitionResult result = order.markFailed(failureCode, this.clock);
		PaymentOutcome outcome = switch (result) {
			case APPLIED -> PaymentOutcome.APPLIED;
			case ALREADY_IN_STATE -> PaymentOutcome.ALREADY_IN_STATE;
			case CONFLICTING_FINAL -> PaymentOutcome.CONFLICTING_FINAL;
		};
		if (result == TransitionResult.APPLIED) {
			appendFailed(order);
			publishEvent(new StockReleaseReadyEvent(order.getId()));
		}
		this.orders.flush();
		return paymentTransition(outcome, order);
	}

	private void publishEvent(Object event) {
		if (this.eventPublisher != null) {
			this.eventPublisher.publishEvent(event);
		}
	}

	private Transition transition(UUID orderId, Function<Order, TransitionResult> change) {
		Order order = lock(orderId);
		TransitionResult result = change.apply(order);
		this.orders.flush();
		return new Transition(result, OrderResponse.of(order));
	}

	private Order lock(UUID orderId) {
		return this.orders.findByIdForUpdate(orderId)
			.orElseThrow(() -> new IllegalStateException("Order to transition does not exist"));
	}

	private Order lockPaymentResult(UUID orderId) {
		return this.orders.findByIdForUpdate(orderId)
			.orElseThrow(() -> new PermanentPaymentResultException(Reason.ORDER_NOT_FOUND));
	}

	private static void validatePayment(Order order, BigDecimal amount, String currency) {
		if (amount == null || order.getTotalAmount().compareTo(amount) != 0) {
			throw new PermanentPaymentResultException(Reason.AMOUNT_MISMATCH);
		}
		if (currency == null || !order.getCurrency().equals(currency)) {
			throw new PermanentPaymentResultException(Reason.CURRENCY_MISMATCH);
		}
	}

	private void appendFailed(Order order) {
		this.outbox.append(OrderEventFactory.ORDER_AGGREGATE, order.getId(), OrderFailedEvent.TYPE,
				eventId -> this.events.failed(eventId, order));
	}

	private static PaymentTransition paymentTransition(PaymentOutcome outcome, Order order) {
		return new PaymentTransition(outcome, OrderResponse.of(order));
	}

}
