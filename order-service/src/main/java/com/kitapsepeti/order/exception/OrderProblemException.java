package com.kitapsepeti.order.exception;

import java.util.UUID;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.ErrorCode;

/**
 * Sipariş uçlarının iş hatası. {@code orderId} doluysa ProblemDetail'e {@code orderId} alanı olarak yazılır: sipariş
 * kaydedildikten sonraki hatalar (sipariş {@code failed}) ve {@code ORDER_PENDING_EXISTS} (mevcut bekleyen sipariş).
 * Kullanıcının kendi siparişinin id'si olduğu için yanıtta sızıntı değildir; log satırlarına yazılmaz.
 */
public class OrderProblemException extends ApiException {

	private final UUID orderId;

	public OrderProblemException(ErrorCode code) {
		this(code, null);
	}

	public OrderProblemException(ErrorCode code, UUID orderId) {
		super(code);
		this.orderId = orderId;
	}

	/** Yanıttaki {@code orderId}; yoksa null. */
	public UUID getOrderId() {
		return this.orderId;
	}

}
