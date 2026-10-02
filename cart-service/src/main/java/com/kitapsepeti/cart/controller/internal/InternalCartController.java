package com.kitapsepeti.cart.controller.internal;

import com.kitapsepeti.cart.dto.internal.CartSnapshotRequest;
import com.kitapsepeti.cart.dto.internal.CartSnapshotResponse;
import com.kitapsepeti.cart.service.CartSnapshotService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servisler arası uçlar (yalnızca {@code X-Internal-Api-Key}, {@link com.kitapsepeti.cart.config.InternalSecurityConfig}).
 * userId gövdede taşınır (yolda değil: loglara ve hata yanıtının {@code instance}'ına girmesin).
 */
@RestController
@RequestMapping(path = "/internal/cart", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalCartController {

	private final CartSnapshotService snapshotService;

	public InternalCartController(CartSnapshotService snapshotService) {
		this.snapshotService = snapshotService;
	}

	/** Aktif sepetin anlık görüntüsü; sepet yoksa da 200 (cartId null). */
	@PostMapping(path = "/snapshot", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CartSnapshotResponse snapshot(@Valid @RequestBody CartSnapshotRequest request) {
		return this.snapshotService.snapshot(request.userId());
	}

}
