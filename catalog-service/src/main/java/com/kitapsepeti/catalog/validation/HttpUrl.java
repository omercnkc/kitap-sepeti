package com.kitapsepeti.catalog.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Mutlak {@code http} veya {@code https} URL'si (host zorunlu). null ve boş metin geçerlidir
 * (opsiyonel alan; PATCH'te boş = temizle). Uzunluk ayrıca {@code @Size} ile sınırlanır.
 */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface HttpUrl {

	String message() default "must be an absolute http or https URL";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
