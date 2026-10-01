package com.kitapsepeti.catalog.controller.internal;

import static com.kitapsepeti.catalog.config.OpenApiConfig.PROBLEM_JSON;
import static com.kitapsepeti.catalog.config.OpenApiConfig.STOCK_PROBLEM_SCHEMA_REF;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.config.OpenApiConfig;
import com.kitapsepeti.catalog.dto.request.ReserveStockRequest;
import com.kitapsepeti.catalog.dto.response.ReservationResponse;
import com.kitapsepeti.catalog.service.StockReservationService;
import com.kitapsepeti.catalog.service.StockReservationService.ReserveResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
 * Servisler arası stok rezervasyonu (InternalSecurityConfig: /internal/** yalnızca API anahtarıyla). Ayrıntılar:
 * docs/api/catalog-internal-stock.md.
 */
@RestController
@RequestMapping(path = "/internal/stock/reservations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_INTERNAL_STOCK, description = "Sipariş için stok ayırma, onay ve iade (yalnızca "
		+ "servisler arası). Rezervasyon 15 dakika geçerlidir; süresi dolan `held` rezervasyonu catalog kendisi "
		+ "serbest bırakır. Ayrıntılar: `docs/api/catalog-internal-stock.md`.")
public class InternalStockController {

	private static final String BASE_PATH = "/internal/stock/reservations/";

	private final StockReservationService reservationService;

	public InternalStockController(StockReservationService reservationService) {
		this.reservationService = reservationService;
	}

	/** Yeni rezervasyon 201; aynı sipariş için aynı istek tekrarı 200 (idempotent). */
	@PostMapping
	@Operation(operationId = "reserveStock", summary = "Stok ayır",
			description = "Ya hep ya hiç: tüm kalemler tek transaction'da ayrılır. Idempotency anahtarı `orderId`: "
					+ "aynı sipariş aynı (bookId, adet) kümesiyle tekrar gönderilirse hiçbir şey değişmez ve mevcut "
					+ "hal 200 ile döner (durum `committed`/`released` olsa bile).")
	@ApiResponse(responseCode = "201", description = "Rezervasyon oluşturuldu (`held`).", headers = @Header(
			name = "Location", description = "Rezervasyonun yolu.",
			schema = @Schema(type = "string", format = "uri-reference")))
	@ApiResponse(responseCode = "200", description = "Aynı sipariş aynı kalemlerle zaten var; mevcut hal.")
	@ApiResponse(responseCode = "409", description = "`INSUFFICIENT_STOCK` veya `BOOK_NOT_AVAILABLE` (`bookIds` ile; "
			+ "hiçbir kalem ayrılmadı) ya da `RESERVATION_MISMATCH` (sipariş için farklı kalemlerle rezervasyon var).",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = STOCK_PROBLEM_SCHEMA_REF),
					examples = @ExampleObject(value = """
							{"title":"Conflict","status":409,"detail":"Not enough stock for one or more books.",\
							"instance":"/internal/stock/reservations","code":"INSUFFICIENT_STOCK",\
							"bookIds":["01920000-0000-7000-8000-000000000402"]}""")))
	public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveStockRequest request) {
		ReserveResult result = reservationService.reserve(request);
		ReservationResponse reservation = result.reservation();
		if (result.created()) {
			return ResponseEntity.created(URI.create(BASE_PATH + reservation.orderId())).body(reservation);
		}
		return ResponseEntity.ok(reservation);
	}

	@GetMapping("/{orderId}")
	@Operation(operationId = "getReservation", summary = "Rezervasyonu oku",
			description = "Süre dolumunu öğrenmek için de kullanılır (ayrı bir olay yoktur): `status: \"released\"`.")
	@ApiResponse(responseCode = "200", description = "Rezervasyon.")
	@ApiResponse(responseCode = "404", description = "`RESOURCE_NOT_FOUND`: sipariş için rezervasyon yok.")
	public ReservationResponse get(@Parameter(description = "Sipariş id'si") @PathVariable UUID orderId) {
		return reservationService.get(orderId);
	}

	@PostMapping("/{orderId}/commit")
	@Operation(operationId = "commitReservation", summary = "Onayla (stoktan düş)",
			description = "`held` kalemler stoktan düşülür. Süresi geçmiş ama hâlâ `held` olan rezervasyon da "
					+ "onaylanır. Zaten `committed` ise değişiklik yok, 200.")
	@ApiResponse(responseCode = "200", description = "Onaylanmış rezervasyon.")
	@ApiResponse(responseCode = "404", description = "`RESOURCE_NOT_FOUND`: sipariş için rezervasyon yok.")
	@ApiResponse(responseCode = "409", description = "`RESERVATION_RELEASED`: rezervasyon iptal edilmiş ya da süresi "
			+ "dolduğu için serbest bırakılmış; order-service ödemeyi iade ederek telafi etmeli.")
	public ReservationResponse commit(@Parameter(description = "Sipariş id'si") @PathVariable UUID orderId) {
		return reservationService.commit(orderId);
	}

	@PostMapping("/{orderId}/release")
	@Operation(operationId = "releaseReservation", summary = "Geri ver (iptal)",
			description = "`held` kalemlerin rezervi geri verilir. Zaten `released` ise değişiklik yok, 200.")
	@ApiResponse(responseCode = "200", description = "Serbest bırakılmış rezervasyon.")
	@ApiResponse(responseCode = "404", description = "`RESOURCE_NOT_FOUND`: sipariş için rezervasyon yok.")
	@ApiResponse(responseCode = "409", description = "`RESERVATION_COMMITTED`: rezervasyon onaylanmış.")
	public ReservationResponse release(@Parameter(description = "Sipariş id'si") @PathVariable UUID orderId) {
		return reservationService.release(orderId);
	}

}
