package com.kitapsepeti.order.controller;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.order.config.OpenApiConfig;
import com.kitapsepeti.order.dto.request.CheckoutRequest;
import com.kitapsepeti.order.dto.request.OrderListRequest;
import com.kitapsepeti.order.dto.response.OrderResponse;
import com.kitapsepeti.order.dto.response.OrderSummaryResponse;
import com.kitapsepeti.order.dto.response.PageResponse;
import com.kitapsepeti.order.security.CurrentUserId;
import com.kitapsepeti.order.service.CheckoutService;
import com.kitapsepeti.order.service.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturumdaki kullanıcının siparişleri; kullanıcı yalnızca token'dan gelir. Başka kullanıcının siparişi olmayan
 * siparişle aynı yanıtı alır (404 {@code ORDER_NOT_FOUND}).
 */
@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_ORDERS, description = "Kullanıcı siparişleri: checkout, detay ve sayfalı özet.")
public class OrderController {

	private final CheckoutService checkoutService;

	private final OrderQueryService orderQueryService;

	public OrderController(CheckoutService checkoutService, OrderQueryService orderQueryService) {
		this.checkoutService = checkoutService;
		this.orderQueryService = orderQueryService;
	}

	/** Aktif sepetten sipariş oluşturur; 201 + {@code Location}. Sonuç ({@code paid}/{@code failed}) GET ile izlenir. */
	@PostMapping(path = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "checkout", summary = "Sipariş oluştur (checkout)",
			description = "Aktif sepetten sipariş oluşturur; 201 + `Location`. Sonuç (`paid`/`failed`) asenkrondur; `GET /api/orders/{orderId}` ile izlenir.")
	@ApiResponse(responseCode = "201", description = "Sipariş oluşturuldu.",
			headers = @Header(name = "Location", description = "Oluşturulan siparişin adresi: `/api/orders/{orderId}`.",
					schema = @Schema(type = "string", format = "uri-reference")))
	@ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` (adres alan hataları `errors` dizisinde) veya `MALFORMED_REQUEST` (geçersiz JSON).")
	@ApiResponse(responseCode = "409", description = "Çakışma: `ORDER_PENDING_EXISTS` (kullanıcının bekleyen siparişi var; `orderId` taşır), `BOOK_NOT_AVAILABLE` (kitap satışta değil) veya `INSUFFICIENT_STOCK` (stok yetersiz; `orderId` taşır).")
	@ApiResponse(responseCode = "422", description = "İş kuralı ihlali: `CART_EMPTY` (sepet boş), `MIXED_CURRENCY` (farklı para birimleri), `ORDER_TOTAL_ZERO` (toplam 0), `ORDER_TOTAL_TOO_LARGE` (üst limit aşımı), `EMPTY_ORDER`, `DUPLICATE_BOOK`, `INVALID_QUANTITY`, `INVALID_PRICE` veya `INVALID_CURRENCY`.")
	@ApiResponse(responseCode = "503", description = "Servis kesintisi: `CART_UNAVAILABLE`, `CATALOG_UNAVAILABLE` (rezervasyon aşamasında `orderId` taşır), `PAYMENT_UNAVAILABLE` (`orderId` taşır), `ORDER_UNAVAILABLE` (`orderId` taşır), `CHECKOUT_INTERRUPTED` (`orderId` taşır) veya `AUTHENTICATION_UNAVAILABLE`.")
	public ResponseEntity<OrderResponse> checkout(@CurrentUserId UUID userId,
			@Valid @RequestBody CheckoutRequest request) {
		OrderResponse order = this.checkoutService.checkout(userId, request.address().toSnapshot());
		return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
	}

	/** Kullanıcının kendi siparişlerinin sayfalı özeti; created_at DESC, id DESC sabit sırasıyla. */
	@GetMapping
	@Operation(operationId = "listOrders", summary = "Siparişleri listele",
			description = "Kullanıcının kendi siparişlerinin sayfalı özeti; `created_at DESC, id DESC` sabit sırasıyla. `itemCount` farklı kitap (satır) sayısıdır; adet toplamı değildir.")
	@ApiResponse(responseCode = "200", description = "Sayfalı sipariş özeti.")
	@ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED`: `page` negatif veya `size` 1–50 aralığı dışında.")
	public PageResponse<OrderSummaryResponse> list(@CurrentUserId UUID userId,
			@Valid @ParameterObject @ModelAttribute OrderListRequest request) {
		return this.orderQueryService.list(userId, request);
	}

	@GetMapping("/{orderId}")
	@Operation(operationId = "getOrder", summary = "Sipariş detayını oku",
			description = "Sipariş detayı; yalnızca siparişin sahibi okuyabilir.")
	@ApiResponse(responseCode = "200", description = "Sipariş detayı.")
	@ApiResponse(responseCode = "400", description = "`MALFORMED_REQUEST`: `orderId` UUID biçiminde değil.")
	@ApiResponse(responseCode = "404", description = "`ORDER_NOT_FOUND`: sipariş bulunamadı veya başka bir kullanıcıya ait.")
	public OrderResponse getOrder(@CurrentUserId UUID userId, @PathVariable UUID orderId) {
		return this.orderQueryService.get(userId, orderId);
	}

}
