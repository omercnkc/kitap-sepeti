package com.kitapsepeti.order.controller;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.order.dto.request.CheckoutRequest;
import com.kitapsepeti.order.dto.response.OrderResponse;
import com.kitapsepeti.order.security.CurrentUserId;
import com.kitapsepeti.order.service.CheckoutService;
import com.kitapsepeti.order.service.OrderQueryService;
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
 * Oturumdaki kullanıcının siparişleri; kullanıcı yalnızca token'dan gelir. Başka kullanıcının siparişi olmayan
 * siparişle aynı yanıtı alır (404 {@code ORDER_NOT_FOUND}).
 */
@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

	private final CheckoutService checkoutService;

	private final OrderQueryService orderQueryService;

	public OrderController(CheckoutService checkoutService, OrderQueryService orderQueryService) {
		this.checkoutService = checkoutService;
		this.orderQueryService = orderQueryService;
	}

	/** Aktif sepetten sipariş oluşturur; 201 + {@code Location}. Sonuç ({@code paid}/{@code failed}) GET ile izlenir. */
	@PostMapping(path = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<OrderResponse> checkout(@CurrentUserId UUID userId,
			@Valid @RequestBody CheckoutRequest request) {
		OrderResponse order = this.checkoutService.checkout(userId, request.address().toSnapshot());
		return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
	}

	@GetMapping("/{orderId}")
	public OrderResponse getOrder(@CurrentUserId UUID userId, @PathVariable UUID orderId) {
		return this.orderQueryService.get(userId, orderId);
	}

}
