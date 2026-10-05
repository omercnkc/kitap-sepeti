package com.kitapsepeti.order.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.kitapsepeti.order.entity.OrderReasons;
import com.kitapsepeti.order.entity.StockState;
import com.kitapsepeti.order.entity.TransitionResult;
import com.kitapsepeti.order.gateway.NotPerformed;
import com.kitapsepeti.order.gateway.PaymentGateway;
import com.kitapsepeti.order.gateway.PaymentInitiationResult;
import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.Unknown;
import com.kitapsepeti.order.repository.OrderRepository;
import com.kitapsepeti.order.repository.PendingReconcileCandidate;
import com.kitapsepeti.order.service.OrderTransactions.PaymentTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bekleyen sipariş uzlaştırma görevi: checkout yarıda kaldıysa veya ödeme olayı kaybolduysa {@code pending} siparişi
 * sonuca bağlar.
 * <ul>
 * <li>stok {@code requested} (rezervasyon sonucu yazılamadı) &rarr; failed {@code CHECKOUT_INTERRUPTED} + release</li>
 * <li>stok {@code held} &rarr; Payment'a checkout'takiyle aynı istek (orderId'ye idempotent): succeeded &rarr; paid
 * (tüketiciyle aynı geçiş), failed &rarr; failed, initiated &rarr; ödeme bağlanır; sonuç yoksa ve sipariş
 * {@code expire-after}'dan eskiyse &rarr; failed {@code ORDER_EXPIRED} + release</li>
 * </ul>
 * Seçim kilitsizdir; Payment çağrısı transaction dışındadır, her geçiş siparişi FOR UPDATE ile yeniden okur. Payment
 * tüketicisiyle aynı siparişe eşzamanlı dokunması {@link TransitionResult} ile idempotenttir. Payment circuit'i açıksa
 * (NotPerformed) o tur başka Payment çağrısı yapılmaz; yalnızca requested ve süresi dolan siparişler işlenir.
 * <p>
 * Tek instance varsayımı geçerlidir (StockSyncJob gibi). Loglar sipariş/ödeme id'si veya tutar içermez.
 */
@Component
@ConditionalOnProperty(name = "app.pending-reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class PendingReconciliationJob {

	private static final Logger log = LoggerFactory.getLogger(PendingReconciliationJob.class);

	/** Tur sonu özetindeki sayaçlar. */
	enum Outcome {
		/** requested &rarr; failed CHECKOUT_INTERRUPTED. */
		INTERRUPTED,
		/** Payment succeeded &rarr; paid. */
		PAID,
		/** Payment failed &rarr; failed. */
		PAYMENT_FAILED,
		/** Süre doldu &rarr; failed ORDER_EXPIRED. */
		EXPIRED,
		/** Payment initiated: ödeme bağlandı, sipariş bekliyor. */
		ATTACHED,
		/** Sonuç yok, süre dolmadı: sonraki tur. */
		WAITING,
		/** Sipariş arada başka yoldan ilerledi (tüketici, checkout); dokunulmadı. */
		SKIPPED,
		/** Geçiş failed siparişe geç ödeme kaydı oldu. */
		LATE_PAYMENT,
		/** Ödeme id'si çelişkisi / beklenmeyen son durum. */
		CONFLICT,
		/** Beklenmeyen hata (ör. kilit zaman aşımı); sonraki tur yeniden dener. */
		ERROR
	}

	private final OrderRepository orders;

	private final OrderTransactions transactions;

	private final PaymentGateway payment;

	private final Clock clock;

	private final Duration minAge;

	private final Duration expireAfter;

	private final int batchSize;

	public PendingReconciliationJob(OrderRepository orders, OrderTransactions transactions, PaymentGateway payment,
			Clock clock, @Value("${app.pending-reconcile.min-age:60s}") Duration minAge,
			@Value("${app.pending-reconcile.expire-after:10m}") Duration expireAfter,
			@Value("${app.pending-reconcile.batch:50}") int batchSize) {
		this.orders = orders;
		this.transactions = transactions;
		this.payment = payment;
		this.clock = clock;
		this.minAge = minAge;
		this.expireAfter = expireAfter;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${app.pending-reconcile.interval:30s}",
			initialDelayString = "${app.pending-reconcile.initial-delay:15s}")
	public void run() {
		executeRound();
	}

	/**
	 * Tek bir uzlaştırma turu. Testlerden doğrudan çağrılabilir.
	 *
	 * @return işlenen aday sayısı
	 */
	public int executeRound() {
		Instant now = this.clock.instant();
		List<PendingReconcileCandidate> candidates = this.orders
			.findPendingReconcileCandidates(now.minus(this.minAge), PageRequest.of(0, this.batchSize));
		if (candidates.isEmpty()) {
			return 0;
		}
		Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
		Round round = new Round(now);
		for (PendingReconcileCandidate candidate : candidates) {
			Outcome outcome;
			try {
				outcome = reconcile(candidate, round);
			}
			catch (RuntimeException ex) {
				log.warn("Pending reconcile failed for an order; retrying next round (error={})",
						ex.getClass().getSimpleName());
				outcome = Outcome.ERROR;
			}
			counts.merge(outcome, 1, Integer::sum);
		}
		if (counts.keySet().stream().anyMatch(outcome -> outcome != Outcome.WAITING)) {
			log.info("Pending reconcile round completed: processed={}, counts=[{}]", candidates.size(),
					summary(counts));
		}
		return candidates.size();
	}

	private Outcome reconcile(PendingReconcileCandidate candidate, Round round) {
		if (candidate.stockState() == StockState.REQUESTED) {
			return fail(candidate, StockState.REQUESTED, OrderReasons.CHECKOUT_INTERRUPTED, Outcome.INTERRUPTED);
		}
		boolean expired = !Duration.between(candidate.createdAt(), round.now).minus(this.expireAfter).isNegative();
		if (round.paymentUnavailable) {
			return expired ? expire(candidate) : Outcome.WAITING;
		}
		PaymentInitiationResult result = this.payment.initiate(candidate.id(), candidate.userId(),
				candidate.totalAmount(), candidate.currency());
		return switch (result) {
			case PaymentInitiationResult.Initiated initiated -> switch (initiated.state()) {
				case SUCCEEDED -> succeeded(candidate, initiated);
				case FAILED -> paymentFailed(candidate, initiated);
				case INITIATED -> initiated(candidate, initiated, expired);
			};
			case NotPerformed notPerformed -> {
				round.paymentUnavailable = true;
				log.warn("Payment unavailable or circuit breaker open; no more payment calls this reconcile round");
				yield expired ? expire(candidate) : Outcome.WAITING;
			}
			case Unknown unknown -> expired ? expire(candidate) : Outcome.WAITING;
			case Rejected rejected -> {
				log.error("Pending reconcile payment rejected by payment service (status={}, code={})",
						rejected.httpStatus(), rejected.code());
				yield expired ? expire(candidate) : Outcome.WAITING;
			}
		};
	}

	private Outcome succeeded(PendingReconcileCandidate candidate, PaymentInitiationResult.Initiated initiated) {
		PaymentTransition transition = this.transactions.reconcilePaymentSucceeded(candidate.id(),
				initiated.paymentId());
		return switch (transition.outcome()) {
			case APPLIED -> Outcome.PAID;
			case ALREADY_IN_STATE -> Outcome.SKIPPED;
			case LATE_PAYMENT_SUCCESS -> {
				log.error("Pending reconcile conflict -> LATE_PAYMENT_SUCCESS");
				yield Outcome.LATE_PAYMENT;
			}
			case LATE_PAYMENT_ID_CONFLICT -> {
				log.error("Pending reconcile conflict -> LATE_PAYMENT_SUCCESS");
				log.error("Pending reconcile conflict -> PAYMENT_ID_CONFLICT");
				yield Outcome.LATE_PAYMENT;
			}
			case PAYMENT_ID_CONFLICT, CONFLICTING_FINAL -> {
				log.error("Pending reconcile conflict -> {}", transition.outcome());
				yield Outcome.CONFLICT;
			}
		};
	}

	private Outcome paymentFailed(PendingReconcileCandidate candidate, PaymentInitiationResult.Initiated initiated) {
		PaymentTransition transition = this.transactions.reconcilePaymentFailed(candidate.id(), initiated.paymentId(),
				OrderReasons.paymentFailureCode(initiated.failureCode()));
		return switch (transition.outcome()) {
			case APPLIED -> Outcome.PAYMENT_FAILED;
			case ALREADY_IN_STATE -> Outcome.SKIPPED;
			case LATE_PAYMENT_SUCCESS, LATE_PAYMENT_ID_CONFLICT, PAYMENT_ID_CONFLICT, CONFLICTING_FINAL -> {
				log.error("Pending reconcile conflict -> {}", transition.outcome());
				yield Outcome.CONFLICT;
			}
		};
	}

	private Outcome initiated(PendingReconcileCandidate candidate, PaymentInitiationResult.Initiated initiated,
			boolean expired) {
		boolean attached = false;
		if (candidate.paymentId() == null) {
			TransitionResult result = this.transactions.attachPayment(candidate.id(), initiated.paymentId()).result();
			if (result == TransitionResult.CONFLICTING_FINAL) {
				log.error("Pending reconcile conflict -> PAYMENT_ID_CONFLICT");
				return Outcome.CONFLICT;
			}
			attached = result == TransitionResult.APPLIED;
		}
		if (expired) {
			return expire(candidate);
		}
		return attached ? Outcome.ATTACHED : Outcome.WAITING;
	}

	private Outcome expire(PendingReconcileCandidate candidate) {
		return fail(candidate, StockState.HELD, OrderReasons.ORDER_EXPIRED, Outcome.EXPIRED);
	}

	private Outcome fail(PendingReconcileCandidate candidate, StockState expectedStock, String failureCode,
			Outcome applied) {
		TransitionResult result = this.transactions
			.failPendingAndReleaseStock(candidate.id(), expectedStock, failureCode)
			.result();
		return result == TransitionResult.APPLIED ? applied : Outcome.SKIPPED;
	}

	private static String summary(Map<Outcome, Integer> counts) {
		return counts.entrySet()
			.stream()
			.map(entry -> entry.getKey().name().toLowerCase(Locale.ROOT) + "=" + entry.getValue())
			.collect(Collectors.joining(", "));
	}

	/** Tur boyunca sabit "şimdi" ve Payment erişilemezlik bayrağı. */
	private static final class Round {

		private final Instant now;

		private boolean paymentUnavailable;

		private Round(Instant now) {
			this.now = now;
		}

	}

}
