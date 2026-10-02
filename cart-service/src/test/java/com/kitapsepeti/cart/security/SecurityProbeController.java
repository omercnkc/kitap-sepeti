package com.kitapsepeti.cart.security;

import java.security.Principal;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Yalnızca testlerde: SecurityConfig kurallarını ve {@link CurrentUserId} çözümünü denemek için uçlar. */
@RestController
class SecurityProbeController {

	@GetMapping("/api/cart/_whoami")
	String whoami(@CurrentUserId UUID userId) {
		return userId.toString();
	}

	/** Principal adı JWT'nin {@code sub} claim'i olmalı. */
	@GetMapping("/api/other/ping")
	String other(Principal principal) {
		return principal.getName();
	}

}
