package com.kitapsepeti.payment.controller.internal;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.payment.dto.request.CreatePaymentRequest;
import com.kitapsepeti.payment.dto.response.PaymentResponse;
import com.kitapsepeti.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servisler arası ödeme uçları (yalnızca {@code X-Internal-Api-Key},
 * {@link com.kitapsepeti.payment.config.InternalSecurityConfig}). Sipariş ve kullanıcı id'si gövdede taşınır; yoldaki
 * ödeme id'si hata yanıtında ve logda {@code :paymentId} olarak maskelenir.
 */
@RestController
@RequestMapping(path = "/internal/payments", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalPaymentController {

	static final String PATH = "/internal/payments";

	private final PaymentService paymentService;

	public InternalPaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	/** Yeni ödeme 201 + Location; aynı sipariş için tekrar istek 200 ve aynı ödeme. */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
		PaymentService.Result result = this.paymentService.create(request);
		if (result.created()) {
			return ResponseEntity.created(URI.create(PATH + "/" + result.payment().paymentId()))
				.body(result.payment());
		}
		return ResponseEntity.ok(result.payment());
	}

	@GetMapping("/{paymentId}")
	public PaymentResponse get(@PathVariable UUID paymentId) {
		return this.paymentService.get(paymentId);
	}

}
