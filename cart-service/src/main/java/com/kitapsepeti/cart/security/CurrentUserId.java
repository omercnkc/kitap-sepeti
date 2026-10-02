package com.kitapsepeti.cart.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller parametresi ({@code UUID}) doğrulanmış JWT'nin {@code sub} claim'inden doldurulur.
 * Kullanıcı kimliği hiçbir zaman istekten (path, query, gövde) alınmaz.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
