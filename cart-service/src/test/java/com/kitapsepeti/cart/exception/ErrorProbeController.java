package com.kitapsepeti.cart.exception;

import java.net.ConnectException;
import java.time.Clock;
import java.util.UUID;

import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.repository.CartRepository;
import com.kitapsepeti.cart.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yalnızca testlerde: GlobalExceptionHandler'ın her dalını tetikler. İstemcinin gönderdiği değerler ({@code bookId},
 * {@code quantity}) parametre olarak alınır; yanıtta ve logda görünmemeleri gerekir.
 */
@RestController
@RequestMapping("/api/cart/_errors")
class ErrorProbeController {

	static final String SECRET_CAUSE = "http://catalog-gizli-host-5512:8082/internal";

	static final String SECRET_MESSAGE = "gizli-ic-detay-4411";

	private final CartRepository cartRepository;

	private final Clock clock;

	ErrorProbeController(CartRepository cartRepository, Clock clock) {
		this.cartRepository = cartRepository;
		this.clock = clock;
	}

	@PostMapping("/line-limit")
	void lineLimit(@RequestParam UUID bookId) {
		throw CartLimitExceededException.lines(50);
	}

	@PostMapping("/quantity-limit")
	void quantityLimit(@RequestParam UUID bookId, @RequestParam int quantity) {
		throw CartLimitExceededException.quantityPerItem(10);
	}

	@PostMapping("/book-not-available")
	void bookNotAvailable(@RequestParam UUID bookId) {
		throw new BookNotAvailableException();
	}

	@PostMapping("/catalog-unavailable")
	void catalogUnavailable(@RequestParam UUID bookId) {
		throw new CatalogUnavailableException(new IllegalStateException("Catalog call failed",
				new ConnectException("Connection refused: " + SECRET_CAUSE)));
	}

	@GetMapping("/illegal-state")
	void illegalState() {
		throw new IllegalStateException(SECRET_MESSAGE);
	}

	@GetMapping("/illegal-argument")
	void illegalArgument() {
		throw new IllegalArgumentException(SECRET_MESSAGE);
	}

	/** İkinci aktif sepet {@code uk_carts_active_user}'a takılır (servis bunu kilitle önler; burada doğrudan yazılır). */
	@PostMapping("/second-active-cart")
	void secondActiveCart(@CurrentUserId UUID userId) {
		cartRepository.saveAndFlush(Cart.openFor(userId, clock));
		cartRepository.saveAndFlush(Cart.openFor(userId, clock));
	}

}
