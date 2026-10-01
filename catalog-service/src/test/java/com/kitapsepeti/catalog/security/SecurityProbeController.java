package com.kitapsepeti.catalog.security;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Yalnızca testlerde: SecurityConfig kurallarını yol bazında denemek için uçlar. */
@RestController
class SecurityProbeController {

	@GetMapping("/api/books/ping")
	String getBooks() {
		return "ok";
	}

	@PostMapping("/api/books/ping")
	String postBooks() {
		return "ok";
	}

	@GetMapping("/api/categories/ping")
	String getCategories() {
		return "ok";
	}

	@GetMapping("/api/admin/ping")
	String admin() {
		return "ok";
	}

	@GetMapping("/internal/ping")
	String internal() {
		return "ok";
	}

	/** Principal adı JWT'nin {@code sub} claim'i olmalı. */
	@GetMapping("/api/other/ping")
	String other(Principal principal) {
		return principal.getName();
	}

}
