package com.kitapsepeti.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link TrMobilePhone} doğrulayıcısı. */
public class TrMobilePhoneValidator implements ConstraintValidator<TrMobilePhone, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return TrPhones.isValidOptional(value);
	}

}
