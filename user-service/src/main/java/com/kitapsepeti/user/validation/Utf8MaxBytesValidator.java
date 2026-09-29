package com.kitapsepeti.user.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class Utf8MaxBytesValidator implements ConstraintValidator<Utf8MaxBytes, CharSequence> {

	private int max;

	@Override
	public void initialize(Utf8MaxBytes annotation) {
		this.max = annotation.value();
	}

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
		return value == null || value.toString().getBytes(StandardCharsets.UTF_8).length <= this.max;
	}

}
