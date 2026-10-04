package com.kitapsepeti.payment.controller.internal;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.payment.config.OpenApiConfig;
import com.kitapsepeti.payment.dto.request.CreatePaymentRequest;
import com.kitapsepeti.payment.dto.response.PaymentResponse;
import com.kitapsepeti.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = OpenApiConfig.TAG_INTERNAL_PAYMENTS, description = "Servisler arası ödeme uçları (v1'de tek istemci "
		+ "order-service). Kullanıcı kimliği gövdede taşınır; yanıtlarda yer almaz.")
public class InternalPaymentController {

	static final String PATH = "/internal/payments";

	private final PaymentService paymentService;

	public InternalPaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	/** Yeni ödeme 201 + Location; aynı sipariş için tekrar istek 200 ve aynı ödeme. */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "createPayment", summary = "Sipariş için ödeme başlat",
			description = "Ödemeyi `initiated` olarak oluşturur ve sağlayıcıya iletir; sonuç webhook ile gelir. "
					+ "Bir siparişin tek ödemesi vardır: aynı `orderId` + `userId` + `amount` + `currency` ile tekrar "
					+ "istek yeni ödeme açmaz, mevcut ödemeyi (durumu ne olursa olsun) 200 ile döndürür.")
	@ApiResponse(responseCode = "201", description = "Ödeme oluşturuldu (`status` = `initiated`).",
			headers = @Header(name = "Location", description = "Oluşan ödemenin yolu: `/internal/payments/{paymentId}`.",
					schema = @Schema(type = "string", format = "uri-reference")))
	@ApiResponse(responseCode = "200", description = "Bu sipariş için ödeme zaten var; mevcut ödeme döner.")
	@ApiResponse(responseCode = "409", description = "`PAYMENT_ORDER_MISMATCH`: sipariş için ödeme var ama `userId`, "
			+ "`amount` veya `currency` farklı. `CONFLICT`: beklenmeyen veri bütünlüğü ihlali.",
			content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON,
					schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
	@ApiResponse(responseCode = "503", description = "`PAYMENT_PROVIDER_UNAVAILABLE`: sağlayıcıya ulaşılamadı; ödeme "
			+ "`initiated` kalır, aynı istek tekrar denenebilir.",
			content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON,
					schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
	public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
		PaymentService.Result result = this.paymentService.create(request);
		if (result.created()) {
			return ResponseEntity.created(URI.create(PATH + "/" + result.payment().paymentId()))
				.body(result.payment());
		}
		return ResponseEntity.ok(result.payment());
	}

	@GetMapping("/{paymentId}")
	@Operation(operationId = "getPayment", summary = "Ödemeyi getir",
			description = "Ödemenin güncel durumu. Webhook gecikirse sonucu öğrenmenin yolu budur.")
	@ApiResponse(responseCode = "200", description = "Ödeme.")
	@ApiResponse(responseCode = "404", description = "`RESOURCE_NOT_FOUND`: bu id ile ödeme yok.",
			content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON,
					schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
	public PaymentResponse get(@Parameter(description = "Ödeme id'si.") @PathVariable UUID paymentId) {
		return this.paymentService.get(paymentId);
	}

}
