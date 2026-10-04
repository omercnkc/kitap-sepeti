package com.kitapsepeti.order;

import java.util.UUID;

import com.kitapsepeti.order.support.JwksServer;
import com.kitapsepeti.order.support.StubServer;
import com.kitapsepeti.order.support.TestClockConfiguration;
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
 * Tam uygulama bağlamı açan testlerin ortak tabanı: MockMvc, Testcontainers MySQL ve RabbitMQ, gerçek HTTP JWKS ucu,
 * Cart/Catalog/Payment yerine WireMock sunucuları ve ileri sarılabilir uygulama saati. Token'lar test JVM'inde üretilen
 * anahtarla imzalanır ({@link TestJwt}); bütün alt sınıflar aynı bağlamı, aynı konteynerleri ve aynı sunucuları
 * paylaşır (alt sınıf kendi {@code @DynamicPropertySource}'unu eklememeli: bağlam önbelleği bölünür).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class, TestClockConfiguration.class })
public abstract class ApiTestSupport {

	private static final JwksServer JWKS = JwksServer.start(TestJwt.publicJwksJson());

	protected static final StubServer CART = StubServer.start();

	protected static final StubServer CATALOG = StubServer.start();

	protected static final StubServer PAYMENT = StubServer.start();

	protected static final String SUBJECT = UUID.randomUUID().toString();

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@DynamicPropertySource
	static void remoteProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::jwkSetUri);
		registry.add("app.clients.cart.base-url", CART::baseUrl);
		registry.add("app.clients.catalog.base-url", CATALOG::baseUrl);
		registry.add("app.clients.payment.base-url", PAYMENT::baseUrl);
	}

	protected static RequestPostProcessor bearer(String token) {
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

}
