package com.kitapsepeti.user.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Doğrulanmış JWT'nin {@code sub} claim'ini {@link java.util.UUID} olarak controller parametresine verir.
 * Kullanıcı id'si YALNIZCA buradan alınır; path veya body'deki bir userId'ye güvenilmez.
 * Principal, resource server'ın ürettiği {@link org.springframework.security.oauth2.jwt.Jwt} nesnesidir.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(subject)")
public @interface CurrentUserId {

}
