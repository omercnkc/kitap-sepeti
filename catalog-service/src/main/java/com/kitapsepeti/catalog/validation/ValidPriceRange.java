package com.kitapsepeti.catalog.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * {@code minPrice} ve {@code maxPrice} ikisi de verildiyse {@code minPrice <= maxPrice} olmalı.
 * Sınıf seviyesinde kural; ihlal {@code minPrice} alanına yazılır (yanıttaki {@code errors[].field}).
 */
@Documented
@Constraint(validatedBy = PriceRangeValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidPriceRange {

	String message() default "must be less than or equal to maxPrice";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
