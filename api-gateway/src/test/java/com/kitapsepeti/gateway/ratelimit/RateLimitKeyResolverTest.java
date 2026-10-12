package com.kitapsepeti.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitKeyResolverTest {

	private KeyResolver ipKeyResolver;
	private KeyResolver userOrIpKeyResolver;

	@BeforeEach
	void setUp() {
		RateLimitKeyResolverConfig config = new RateLimitKeyResolverConfig();
		this.ipKeyResolver = config.ipKeyResolver();
		this.userOrIpKeyResolver = config.userOrIpKeyResolver();
	}

	@Test
	void ipKeyResolver_usesFirstXForwardedForHop() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/auth/login")
						.header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
						.remoteAddress(new InetSocketAddress("192.168.1.1", 5000))
						.build());

		assertThat(this.ipKeyResolver.resolve(exchange).block()).isEqualTo("203.0.113.10");
	}

	@Test
	void ipKeyResolver_trimsXForwardedForFirstHop() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/")
						.header("X-Forwarded-For", "  198.51.100.7  , 10.0.0.2")
						.build());

		assertThat(this.ipKeyResolver.resolve(exchange).block()).isEqualTo("198.51.100.7");
	}

	@Test
	void ipKeyResolver_fallsBackToRemoteAddressWhenXffMissing() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/")
						.remoteAddress(new InetSocketAddress("10.1.2.3", 443))
						.build());

		assertThat(this.ipKeyResolver.resolve(exchange).block()).isEqualTo("10.1.2.3");
	}

	@Test
	void ipKeyResolver_returnsUnknownWhenNoXffAndNoRemoteAddress() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/").build());

		assertThat(this.ipKeyResolver.resolve(exchange).block()).isEqualTo("unknown");
	}

	@Test
	void userOrIpKeyResolver_usesUserPrefixWhenXUserIdPresent() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/cart")
						.header("X-User-Id", "user-42")
						.header("X-Forwarded-For", "203.0.113.10")
						.build());

		assertThat(this.userOrIpKeyResolver.resolve(exchange).block()).isEqualTo("user:user-42");
	}

	@Test
	void userOrIpKeyResolver_trimsXUserId() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/cart")
						.header("X-User-Id", "  abc-1  ")
						.build());

		assertThat(this.userOrIpKeyResolver.resolve(exchange).block()).isEqualTo("user:abc-1");
	}

	@Test
	void userOrIpKeyResolver_fallsBackToIpPrefixWhenXUserIdMissing() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/cart")
						.header("X-Forwarded-For", "203.0.113.99")
						.build());

		assertThat(this.userOrIpKeyResolver.resolve(exchange).block()).isEqualTo("ip:203.0.113.99");
	}

	@Test
	void userOrIpKeyResolver_fallsBackToIpPrefixWhenXUserIdBlank() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/cart")
						.header("X-User-Id", "   ")
						.remoteAddress(new InetSocketAddress("172.16.0.5", 80))
						.build());

		assertThat(this.userOrIpKeyResolver.resolve(exchange).block()).isEqualTo("ip:172.16.0.5");
	}

	@Test
	void userOrIpKeyResolver_unknownWhenNoUserAndNoIp() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/cart").build());

		assertThat(this.userOrIpKeyResolver.resolve(exchange).block()).isEqualTo("ip:unknown");
	}

}
