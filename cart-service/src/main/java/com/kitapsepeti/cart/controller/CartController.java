package com.kitapsepeti.cart.controller;

import java.util.UUID;

import com.kitapsepeti.cart.dto.request.AddCartItemRequest;
import com.kitapsepeti.cart.dto.request.UpdateCartItemRequest;
import com.kitapsepeti.cart.dto.response.CartResponse;
import com.kitapsepeti.cart.security.CurrentUserId;
import com.kitapsepeti.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oturumdaki kullanıcının sepeti; kullanıcı yalnızca token'dan gelir, başka kullanıcının sepetine yol yoktur. */
@RestController
@RequestMapping(path = "/api/cart", produces = MediaType.APPLICATION_JSON_VALUE)
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	@GetMapping
	public CartResponse getCart(@CurrentUserId UUID userId) {
		return this.cartService.getCart(userId);
	}

	/** Sepeti yoksa açar; kitap sepetteyse adedini artırır. Yanıt güncel sepettir (200). */
	@PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CartResponse addItem(@CurrentUserId UUID userId, @Valid @RequestBody AddCartItemRequest request) {
		return this.cartService.addItem(userId, request);
	}

	/** Adedi verilen değere ayarlar; kitap sepette değilse 404. */
	@PatchMapping(path = "/items/{bookId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public CartResponse changeQuantity(@CurrentUserId UUID userId, @PathVariable UUID bookId,
			@Valid @RequestBody UpdateCartItemRequest request) {
		return this.cartService.changeQuantity(userId, bookId, request);
	}

	/** Idempotent: kitap sepette değilse de 200. */
	@DeleteMapping("/items/{bookId}")
	public CartResponse removeItem(@CurrentUserId UUID userId, @PathVariable UUID bookId) {
		return this.cartService.removeItem(userId, bookId);
	}

	/** Sepeti boşaltır; sepet aktif kalır. */
	@DeleteMapping("/items")
	public CartResponse clear(@CurrentUserId UUID userId) {
		return this.cartService.clear(userId);
	}

}
