package com.kitapsepeti.catalog.controller.internal;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.ReserveStockRequest;
import com.kitapsepeti.catalog.dto.response.ReservationResponse;
import com.kitapsepeti.catalog.service.StockReservationService;
import com.kitapsepeti.catalog.service.StockReservationService.ReserveResult;
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
 * Servisler arası stok rezervasyonu (InternalSecurityConfig: /internal/** yalnızca API anahtarıyla). Ayrıntılar:
 * docs/api/catalog-internal-stock.md.
 */
@RestController
@RequestMapping(path = "/internal/stock/reservations", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalStockController {

	private static final String BASE_PATH = "/internal/stock/reservations/";

	private final StockReservationService reservationService;

	public InternalStockController(StockReservationService reservationService) {
		this.reservationService = reservationService;
	}

	/** Yeni rezervasyon 201; aynı sipariş için aynı istek tekrarı 200 (idempotent). */
	@PostMapping
	public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveStockRequest request) {
		ReserveResult result = reservationService.reserve(request);
		ReservationResponse reservation = result.reservation();
		if (result.created()) {
			return ResponseEntity.created(URI.create(BASE_PATH + reservation.orderId())).body(reservation);
		}
		return ResponseEntity.ok(reservation);
	}

	@GetMapping("/{orderId}")
	public ReservationResponse get(@PathVariable UUID orderId) {
		return reservationService.get(orderId);
	}

	@PostMapping("/{orderId}/commit")
	public ReservationResponse commit(@PathVariable UUID orderId) {
		return reservationService.commit(orderId);
	}

	@PostMapping("/{orderId}/release")
	public ReservationResponse release(@PathVariable UUID orderId) {
		return reservationService.release(orderId);
	}

}
