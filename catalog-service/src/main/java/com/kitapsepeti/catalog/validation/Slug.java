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
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Slug: küçük harf/rakam grupları, aralarında tek "-" (ör. {@code can-yayinlari}); uzunluk kolon sınırını aşamaz.
 * null geçerlidir (slug isteğe bağlı; verilmezse addan üretilir / değiştirilmez).
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
@Size
@ReportAsSingleViolation
@Target({ METHOD, FIELD, ANNOTATION_TYPE, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface Slug {

	@OverridesAttribute(constraint = Size.class, name = "max")
	int max();

	String message() default "must consist of lowercase letters and digits separated by single hyphens, "
			+ "at most {max} characters";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
