package com.kitapsepeti.order.client.cart;

import com.kitapsepeti.order.client.ClientHeaders;
import com.kitapsepeti.order.client.InternalClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Cart internal API (docs/api/cart-service.openapi.json). Doğrudan kullanılmaz; {@link FeignCartGateway} üzerinden. */
@FeignClient(name = "cart", url = "${app.clients.cart.base-url}", configuration = InternalClientConfiguration.class)
public interface CartClient {

	@PostMapping(path = "/internal/cart/snapshot", consumes = MediaType.APPLICATION_JSON_VALUE,
			headers = ClientHeaders.ACCEPT_JSON)
	ResponseEntity<CartSnapshotResponse> snapshot(@RequestBody CartSnapshotRequest request);

}
