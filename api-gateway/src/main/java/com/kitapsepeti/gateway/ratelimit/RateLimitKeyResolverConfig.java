package com.kitapsepeti.gateway.ratelimit;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate limit kota anahtarları (Adım 3). Route’lara {@code RequestRateLimiter} bağlama Adım 4.
 *
 * <p>Key formatı:
 * <ul>
 *   <li>{@code ipKeyResolver}: ham IP veya {@code unknown} (prefix yok)</li>
 *   <li>{@code userOrIpKeyResolver}: {@code user:} + id veya {@code ip:} + IP/{@code unknown}</li>
 * </ul>
 * JWT yeniden parse edilmez; korumalı uçlarda {@code JwtAuthGlobalFilter} yazdığı {@code X-User-Id} kullanılır.
 */
@Configuration
public class RateLimitKeyResolverConfig {

	static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
	static final String HEADER_X_USER_ID = "X-User-Id";
	static final String UNKNOWN = "unknown";

	@Bean
	public KeyResolver ipKeyResolver() {
		return exchange -> Mono.just(resolveClientIp(exchange));
	}

	/**
	 * Varsayılan KeyResolver (@Primary): Gateway RequestRateLimiter factory tek bean ister;
	 * Adım 4’te default tier = user→IP. Route’lar {@code #{@ipKeyResolver}} ile override eder.
	 */
	@Bean
	@Primary
	public KeyResolver userOrIpKeyResolver() {
		return exchange -> {
			String userId = exchange.getRequest().getHeaders().getFirst(HEADER_X_USER_ID);
			if (userId != null && !userId.isBlank()) {
				return Mono.just("user:" + userId.trim());
			}
			return Mono.just("ip:" + resolveClientIp(exchange));
		};
	}

	/**
	 * X-Forwarded-For ilk hop (trim); yoksa remoteAddress host; yoksa {@code unknown}.
	 */
	static String resolveClientIp(ServerWebExchange exchange) {
		String xff = exchange.getRequest().getHeaders().getFirst(HEADER_X_FORWARDED_FOR);
		if (xff != null && !xff.isBlank()) {
			String firstHop = xff.split(",", 2)[0].trim();
			if (!firstHop.isEmpty()) {
				return firstHop;
			}
		}
		InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
		if (remote != null && remote.getAddress() != null) {
			String host = remote.getAddress().getHostAddress();
			if (host != null && !host.isBlank()) {
				return host;
			}
		}
		return UNKNOWN;
	}

}
