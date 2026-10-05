package com.kitapsepeti.user.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
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
 * TR cep telefonu. null/boş geçerli (opsiyonel veya PATCH {@code ""} = sil).
 * Doluysa {@code 05…} / {@code 5…} / {@code +905…} kabul; kanonik {@code ^5\d{9}$}.
 */
@Documented
@Constraint(validatedBy = TrMobilePhoneValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface TrMobilePhone {

	String message() default "must be a valid TR mobile phone";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
