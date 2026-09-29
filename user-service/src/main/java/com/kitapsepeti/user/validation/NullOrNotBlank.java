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
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH isteklerindeki zorunlu alanlar için: null = "değiştirme" (geçerli); gönderildiyse boş veya
 * yalnızca boşluk olamaz.
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL)
@ReportAsSingleViolation
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface NullOrNotBlank {

	String message() default "must not be blank";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
