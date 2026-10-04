package com.kitapsepeti.order.client.catalog;

import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.client.ClientHeaders;
import com.kitapsepeti.order.client.InternalClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Catalog public kitap okuma ve internal stok rezervasyonu (docs/api/catalog-service.openapi.json). Doğrudan
 * kullanılmaz; {@link FeignCatalogGateway} üzerinden.
 */
@FeignClient(name = "catalog", url = "${app.clients.catalog.base-url}",
		configuration = InternalClientConfiguration.class)
public interface CatalogClient {

	@GetMapping(path = "/api/books/lookup", headers = ClientHeaders.ACCEPT_JSON)
	ResponseEntity<BookLookupResponse> lookup(@RequestParam("ids") List<UUID> ids);

	@PostMapping(path = "/internal/stock/reservations", consumes = MediaType.APPLICATION_JSON_VALUE,
			headers = ClientHeaders.ACCEPT_JSON)
	ResponseEntity<ReservationResponse> reserve(@RequestBody ReserveStockRequest request);

	@PostMapping(path = "/internal/stock/reservations/{orderId}/commit", headers = ClientHeaders.ACCEPT_JSON)
	ResponseEntity<ReservationResponse> commit(@PathVariable("orderId") UUID orderId);

	@PostMapping(path = "/internal/stock/reservations/{orderId}/release", headers = ClientHeaders.ACCEPT_JSON)
	ResponseEntity<ReservationResponse> release(@PathVariable("orderId") UUID orderId);

}
