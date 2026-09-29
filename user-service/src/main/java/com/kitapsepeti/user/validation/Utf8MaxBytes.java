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
 * Metnin UTF-8 byte uzunluğu en fazla {@link #value()} olmalı. BCrypt yalnızca ilk 72 byte'ı kullanır
 * (72'den uzununu Spring reddeder); {@code @Size} karakter saydığı için "ş" gibi 2 byte'lık harflerle
 * bu sınır aşılabilir. null geçerlidir; boşluk kontrolü {@code @NotBlank}'in işidir.
 */
@Documented
@Constraint(validatedBy = Utf8MaxBytesValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface Utf8MaxBytes {

	int value();

	String message() default "must be at most {value} bytes in UTF-8";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
