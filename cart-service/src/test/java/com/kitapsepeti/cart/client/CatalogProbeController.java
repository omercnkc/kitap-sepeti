package com.kitapsepeti.cart.client;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yalnızca testlerde: gateway'i kimliği doğrulanmış gerçek bir istek içinden çağırır (security context'te kullanıcı
 * token'ı var) ve hataları GlobalExceptionHandler'dan geçirir.
 */
@RestController
@RequestMapping("/api/cart/_catalog")
class CatalogProbeController {

	private final CatalogGateway gateway;

	CatalogProbeController(CatalogGateway gateway) {
		this.gateway = gateway;
	}

	@GetMapping("/books/{id}")
	String book(@PathVariable UUID id) {
		return this.gateway.requireAvailableBook(id).title();
	}

	@GetMapping("/lookup")
	List<String> lookup(@RequestParam List<UUID> ids) {
		return this.gateway.lookup(ids).keySet().stream().map(UUID::toString).toList();
	}

}
