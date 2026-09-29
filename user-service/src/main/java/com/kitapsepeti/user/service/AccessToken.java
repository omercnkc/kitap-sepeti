package com.kitapsepeti.user.service;

import java.time.Instant;

/**
 * İmzalanmış access token ve bitiş anı.
 *
 * @param value     compact JWT ({@code header.payload.signature})
 * @param expiresAt token'daki {@code exp} ile aynı an
 */
public record AccessToken(String value, Instant expiresAt) {
}
