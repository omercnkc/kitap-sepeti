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
import com.kitapsepeti.order.entity.TransitionResult;
import com.kitapsepeti.order.repository.OrderRepository;
import com.kitapsepeti.order.service.PermanentPaymentResultException.Reason;
import com.kitapsepeti.order.service.event.CartCheckedOutEvent;
import com.kitapsepeti.order.service.event.OrderFailedEvent;
import com.kitapsepeti.order.service.event.OrderPaidEvent;
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

	public OrderTransactions(OrderRepository orders, OutboxService outbox, OrderEventFactory events, Clock clock) {
		this.orders = orders;
		this.outbox = outbox;
		this.events = events;
		this.clock = clock;
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
		LATE_PAYMENT_SUCCESS,
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
		if (order.getPaymentId() != null && !order.getPaymentId().equals(paymentId)) {
			return paymentTransition(PaymentOutcome.PAYMENT_ID_CONFLICT, order);
		}
		OrderStatus before = order.getStatus();
		TransitionResult result = order.markPaid(paymentId, this.clock);
		PaymentOutcome outcome = switch (result) {
			case APPLIED -> PaymentOutcome.APPLIED;
			case ALREADY_IN_STATE -> PaymentOutcome.ALREADY_IN_STATE;
			case CONFLICTING_FINAL -> before == OrderStatus.FAILED ? PaymentOutcome.LATE_PAYMENT_SUCCESS
					: PaymentOutcome.CONFLICTING_FINAL;
		};
		if (result == TransitionResult.APPLIED) {
			this.outbox.append(OrderEventFactory.ORDER_AGGREGATE, order.getId(), OrderPaidEvent.TYPE,
					eventId -> this.events.paid(eventId, order));
			this.outbox.append(OrderEventFactory.ORDER_AGGREGATE, order.getId(), CartCheckedOutEvent.TYPE,
					eventId -> this.events.cartCheckedOut(eventId, order));
		}
		this.orders.flush();
		return paymentTransition(outcome, order);
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
		}
		this.orders.flush();
		return paymentTransition(outcome, order);
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
