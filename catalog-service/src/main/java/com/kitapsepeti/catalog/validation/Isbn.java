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
 * Geçerli ISBN-10 veya ISBN-13 (checksum dahil); değer önce {@link Isbns#normalize} ile normalize edilir.
 * null ve boş metin geçerlidir (opsiyonel alan; PATCH'te boş = temizle).
 */
@Documented
@Constraint(validatedBy = IsbnValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface Isbn {

	String message() default "must be a valid ISBN-10 or ISBN-13";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
