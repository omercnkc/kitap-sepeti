package com.kitapsepeti.cart.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Catalog public okuma uçları ({@code docs/api/catalog-service.openapi.json}). Yalnızca {@link CatalogGateway} kullanır;
 * Feign hataları dışarı orada çevrilir. Kullanıcının token'ı ya da başka bir header'ı taşıyan RequestInterceptor YOK
 * (uçlar public). Zaman aşımları {@code spring.cloud.openfeign.client.config.catalog}.
 */
@FeignClient(name = "catalog", url = "${app.catalog.base-url}")
public interface CatalogClient {

	/** Yalnızca yayındaki kitap; diğerleri 404. */
	@GetMapping(path = "/api/books/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	CatalogBook getBook(@PathVariable("id") UUID id);

	/** {@code ids=a&ids=b} biçiminde gider; en fazla {@link CatalogGateway#MAX_LOOKUP_IDS}. */
	@GetMapping(path = "/api/books/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
	CatalogBookLookup lookup(@RequestParam("ids") List<UUID> ids);

}
