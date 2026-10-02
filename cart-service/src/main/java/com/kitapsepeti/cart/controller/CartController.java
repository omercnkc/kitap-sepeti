package com.kitapsepeti.cart.controller;

import java.util.UUID;

import com.kitapsepeti.cart.dto.request.AddCartItemRequest;
import com.kitapsepeti.cart.dto.response.CartResponse;
import com.kitapsepeti.cart.security.CurrentUserId;
import com.kitapsepeti.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
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

}
