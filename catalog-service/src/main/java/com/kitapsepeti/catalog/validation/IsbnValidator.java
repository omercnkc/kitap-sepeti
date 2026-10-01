package com.kitapsepeti.catalog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link Isbn} doğrulayıcısı. */
public class IsbnValidator implements ConstraintValidator<Isbn, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isEmpty()) {
			return true;
		}
		return Isbns.isValid(Isbns.normalize(value));
	}

}
