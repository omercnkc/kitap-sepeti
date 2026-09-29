package com.kitapsepeti.user.exception;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Yalnızca testte: sınıf seviyesinde {@code @Validated} → ihlalde {@code ConstraintViolationException}. */
@Service
@Validated
public class ValidatedTestService {

	public void check(@Size(max = 5) String name, @Email String email) {
	}

}
