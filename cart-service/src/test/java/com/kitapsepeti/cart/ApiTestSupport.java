package com.kitapsepeti.cart;

import java.util.UUID;

import com.kitapsepeti.cart.repository.CartRepository;
import com.kitapsepeti.cart.support.CatalogStub;
import com.kitapsepeti.cart.support.JwksServer;
import com.kitapsepeti.cart.support.MutableClock;
import com.kitapsepeti.cart.support.MutableClockConfiguration;
import com.kitapsepeti.cart.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Tam uygulama bağlamı açan testlerin ortak tabanı: MockMvc, Testcontainers MySQL, gerçek HTTP JWKS ucu ve
 * Catalog taklidi ({@link CatalogStub}, {@code app.catalog.base-url}).
 * Token'lar test JVM'inde üretilen anahtarla imzalanır ({@link TestJwt}); bütün alt sınıflar aynı bağlamı,
 * aynı konteyneri ve aynı JWKS sunucusunu paylaşır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
public abstract class ApiTestSupport {

	private static final JwksServer JWKS = JwksServer.start(TestJwt.publicJwksJson());

	/** Catalog taklidi; her test başında kayıtları ve yanıtı sıfırlanır. */
	protected static final CatalogStub CATALOG = CatalogStub.start();

	protected static final String SUBJECT = UUID.randomUUID().toString();

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	/** Gerçek repository; yarış senaryosu testleri tek tek metotları taklit eder (her testten sonra sıfırlanır). */
	@MockitoSpyBean
	protected CartRepository carts;

	@DynamicPropertySource
	static void jwksProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::jwkSetUri);
		registry.add("app.catalog.base-url", CATALOG::baseUrl);
	}

	@BeforeEach
	void resetState() {
		clock.useSystemTime();
		CATALOG.reset();
		jdbc.update("DELETE FROM cart_items");
		jdbc.update("DELETE FROM carts");
	}

	protected static RequestPostProcessor bearer(String token) {
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

}
