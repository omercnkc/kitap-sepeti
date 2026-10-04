package com.kitapsepeti.order.client.payment;

import com.kitapsepeti.order.client.ClientHeaders;
import com.kitapsepeti.order.client.InternalClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Payment internal API (docs/api/payment-service.openapi.json). Doğrudan kullanılmaz; {@link FeignPaymentGateway}. */
@FeignClient(name = "payment", url = "${app.clients.payment.base-url}",
		configuration = InternalClientConfiguration.class)
public interface PaymentClient {

	@PostMapping(path = "/internal/payments", consumes = MediaType.APPLICATION_JSON_VALUE,
			headers = ClientHeaders.ACCEPT_JSON)
	ResponseEntity<PaymentResponse> create(@RequestBody CreatePaymentRequest request);

}
