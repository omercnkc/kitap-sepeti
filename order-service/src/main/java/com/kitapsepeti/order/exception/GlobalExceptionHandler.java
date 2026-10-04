package com.kitapsepeti.order.exception;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.common.error.ProblemDetailExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir; ortak handler'lar tabandan gelir.
 * Burada yalnızca siparişe özgü olanlar var: {@link OrderProblemException}'ın {@code orderId} alanı ve DB kısıtı → kod
 * eşlemesi. Yol, servisin {@code RequestPathMasker} bean'iyle maskelenir ({@code SecurityConfig}).
 * Security filtrelerinde oluşan 401/403/503 buraya ulaşmaz; onları common'daki security handler'ları yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

	static final String ORDER_ID_PROPERTY = "orderId";

	static final String PENDING_USER_CONSTRAINT = "uk_orders_pending_user";

	@Override
	protected void addProperties(ProblemDetail problem, ApiException ex) {
		if (ex instanceof OrderProblemException orderProblem && orderProblem.getOrderId() != null) {
			problem.setProperty(ORDER_ID_PROPERTY, orderProblem.getOrderId());
		}
	}

	/**
	 * Checkout bekleyen sipariş yarışını kendisi çözer (mevcut siparişin id'siyle 409); buraya ulaşan ihlal yine
	 * 409 {@code ORDER_PENDING_EXISTS} olur. Logda yalnızca kısıt adı (DB mesajı kullanıcı id'si içerir).
	 */
	@Override
	protected ConstraintOutcome classify(DataIntegrityViolationException ex) {
		if (DbConstraints.isViolated(ex, PENDING_USER_CONSTRAINT)) {
			return new ConstraintOutcome(OrderErrorCode.ORDER_PENDING_EXISTS, "constraint=" + PENDING_USER_CONSTRAINT);
		}
		return super.classify(ex);
	}

}
