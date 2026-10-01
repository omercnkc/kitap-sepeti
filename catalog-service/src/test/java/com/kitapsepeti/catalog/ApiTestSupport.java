package com.kitapsepeti.catalog;

import java.util.UUID;

import com.kitapsepeti.catalog.support.JwksServer;
import com.kitapsepeti.catalog.support.TestJwt;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * API testlerinin ortak tabanı: tam uygulama bağlamı, MockMvc ve Testcontainers MySQL.
 * Token'lar user-service'in test anahtarıyla imzalanır; doğrulayıcı açık anahtarı gerçek bir HTTP JWKS
 * ucundan çeker (bütün alt sınıflar aynı bağlamı ve aynı sunucuyu paylaşır).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
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

	@BeforeEach
	void cleanDatabase() {
		jdbc.update("DELETE FROM outbox");
		jdbc.update("DELETE FROM book_authors");
		jdbc.update("DELETE FROM book_categories");
		jdbc.update("DELETE FROM stock_reservations");
		jdbc.update("DELETE FROM books");
		jdbc.update("DELETE FROM authors");
		jdbc.update("UPDATE categories SET parent_id = NULL");
		jdbc.update("DELETE FROM categories");
		jdbc.update("DELETE FROM publishers");
	}

	protected static RequestPostProcessor bearer(String token) {
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

}
