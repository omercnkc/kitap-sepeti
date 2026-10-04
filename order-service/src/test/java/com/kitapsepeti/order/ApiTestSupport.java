package com.kitapsepeti.order;

import java.util.UUID;

import com.kitapsepeti.order.support.JwksServer;
import com.kitapsepeti.order.support.TestJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Tam uygulama bağlamı açan testlerin ortak tabanı: MockMvc, Testcontainers MySQL ve RabbitMQ, gerçek HTTP JWKS ucu.
 * Token'lar test JVM'inde üretilen anahtarla imzalanır ({@link TestJwt}); bütün alt sınıflar aynı bağlamı,
 * aynı konteynerleri ve aynı JWKS sunucusunu paylaşır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class })
public abstract class ApiTestSupport {

	private static final JwksServer JWKS = JwksServer.start(TestJwt.publicJwksJson());

	protected static final String SUBJECT = UUID.randomUUID().toString();

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@DynamicPropertySource
	static void jwksProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::jwkSetUri);
	}

	protected static RequestPostProcessor bearer(String token) {
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

}
