package com.kitapsepeti.order.client.payment;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.order.client.CallOutcome;
import com.kitapsepeti.order.client.Downstream;
import com.kitapsepeti.order.client.InvalidResponseException;
import com.kitapsepeti.order.client.RemoteCalls;
import com.kitapsepeti.order.gateway.NotPerformed;
import com.kitapsepeti.order.gateway.PaymentGateway;
import com.kitapsepeti.order.gateway.PaymentInitiationResult;
import com.kitapsepeti.order.gateway.PaymentState;
import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.Unknown;
import org.springframework.stereotype.Component;

/**
 * Payment ödeme başlatma. 503 {@code PAYMENT_PROVIDER_UNAVAILABLE} diğer 5xx'ler gibi {@link Unknown}: Payment ödeme
 * satırını oluşturmuş olabilir, aynı istekle tekrar onu tamamlar.
 */
@Component
public class FeignPaymentGateway implements PaymentGateway {

	private final PaymentClient client;

	private final RemoteCalls calls;

	public FeignPaymentGateway(PaymentClient client, RemoteCalls calls) {
		this.client = client;
		this.calls = calls;
	}

	@Override
	public PaymentInitiationResult initiate(UUID orderId, UUID userId, BigDecimal amount, String currency) {
		CreatePaymentRequest request = new CreatePaymentRequest(orderId, userId, amount, currency);
		return this.calls.execute(Downstream.PAYMENT, "initiate", () -> this.client.create(request),
				body -> state(body, orderId), FeignPaymentGateway::toResult);
	}

	private static PaymentState state(PaymentResponse body, UUID orderId) {
		if (!orderId.equals(body.orderId())) {
			throw new InvalidResponseException("Payment belongs to another order");
		}
		return switch (body.status()) {
			case "initiated" -> PaymentState.INITIATED;
			case "succeeded" -> PaymentState.SUCCEEDED;
			case "failed" -> PaymentState.FAILED;
			default -> throw new InvalidResponseException("Payment status is not recognised");
		};
	}

	private static PaymentInitiationResult toResult(CallOutcome<PaymentResponse> outcome) {
		return switch (outcome) {
			case CallOutcome.Success<PaymentResponse> success -> new PaymentInitiationResult.Initiated(
					success.body().paymentId(), state(success.body(), success.body().orderId()),
					success.body().failureCode());
			case CallOutcome.Problem<PaymentResponse> problem -> new Rejected(problem.status(), problem.code());
			case CallOutcome.NotSent<PaymentResponse> notSent -> new NotPerformed();
			case CallOutcome.Failed<PaymentResponse> failed -> new Unknown();
		};
	}

}
