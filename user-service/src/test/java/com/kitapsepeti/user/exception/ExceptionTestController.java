package com.kitapsepeti.user.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yalnızca testte var olan, bilerek hata fırlatan uçlar. Yollar permitAll listesinde olmadığı için
 * testlerde {@code @WithMockUser} ile erişilir.
 */
@RestController
@RequestMapping("/test/exceptions")
public class ExceptionTestController {

	public record SampleRequest(@Email String email, @Size(min = 8, max = 72) String password) {
	}

	@GetMapping("/email-exists")
	public void emailExists() {
		throw new EmailAlreadyExistsException();
	}

	@PostMapping("/validate")
	public void validate(@Valid @RequestBody SampleRequest request) {
	}

	@GetMapping("/data-integrity")
	public void dataIntegrity() {
		throw new DataIntegrityViolationException("Duplicate entry 'x@y.com' for key 'users.uk_users_email'");
	}

	@GetMapping("/unexpected")
	public void unexpected() {
		throw new RuntimeException("gizli detay");
	}

	@GetMapping("/admin")
	@PreAuthorize("hasRole('ADMIN')")
	public void adminOnly() {
	}

}
