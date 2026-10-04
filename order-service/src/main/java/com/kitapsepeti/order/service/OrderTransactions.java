package com.kitapsepeti.order.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.kitapsepeti.order.dto.response.OrderResponse;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.TransitionResult;
import com.kitapsepeti.order.repository.OrderRepository;
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

	private final Clock clock;

	public OrderTransactions(OrderRepository orders, Clock clock) {
		this.orders = orders;
		this.clock = clock;
	}

	/** Geçişin sonucu ve siparişin geçişten sonraki (uygulanmadıysa mevcut) hali. */
	public record Transition(TransitionResult result, OrderResponse order) {

		public boolean applied() {
			return this.result == TransitionResult.APPLIED;
		}

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

	/** {@code pending → failed}; stok durumu korunur (bırakma Adım 5). */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition markFailed(UUID orderId, String failureCode) {
		return transition(orderId, order -> order.markFailed(failureCode, this.clock));
	}

	/** Payment'ta oluşturulan ödemeyi siparişe bağlar. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Transition attachPayment(UUID orderId, UUID paymentId) {
		return transition(orderId, order -> order.attachPayment(paymentId, this.clock));
	}

	private Transition transition(UUID orderId, Function<Order, TransitionResult> change) {
		Order order = this.orders.findByIdForUpdate(orderId)
			.orElseThrow(() -> new IllegalStateException("Order to transition does not exist"));
		TransitionResult result = change.apply(order);
		this.orders.flush();
		return new Transition(result, OrderResponse.of(order));
	}

}
