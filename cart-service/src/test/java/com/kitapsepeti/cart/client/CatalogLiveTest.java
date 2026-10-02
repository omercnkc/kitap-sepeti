package com.kitapsepeti.cart.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kitapsepeti.cart.TestcontainersConfiguration;
import com.kitapsepeti.cart.support.InternalTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Çalışan gerçek Catalog'a karşı (varsayılan build'de ATLANIR). Catalog seed verisi yüklü olmalı.
 * Çalıştırma: {@code .\mvnw.cmd -pl cart-service test "-Dtest=CatalogLiveTest" "-Dcatalog.live=true"}
 * (adres {@code -Dcatalog.live.base-url}, varsayılan {@code http://127.0.0.1:8082}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "catalog.live", matches = "true")
class CatalogLiveTest {

	private static final UUID PUBLISHED_1 = UUID.fromString("01920000-0000-7000-8000-000000000401");

	private static final UUID PUBLISHED_2 = UUID.fromString("01920000-0000-7000-8000-000000000402");

	private static final UUID DRAFT = UUID.fromString("01920000-0000-7000-8000-000000000412");

	@Autowired
	private CatalogGateway gateway;

	@DynamicPropertySource
	static void catalogProperties(DynamicPropertyRegistry registry) {
		registry.add("app.catalog.base-url", () -> System.getProperty("catalog.live.base-url", "http://127.0.0.1:8082"));
		InternalTestKeys.register(registry);
	}

	@Test
	void requireAvailableBookReadsPublishedSeedBook() {
		CatalogBook book = gateway.requireAvailableBook(PUBLISHED_1);

		assertThat(book.id()).isEqualTo(PUBLISHED_1);
		assertThat(book.title()).isNotBlank();
		assertThat(book.priceAmount().scale()).isEqualTo(2);
		assertThat(book.currency()).isEqualTo("TRY");
		assertThat(book.inStock()).isTrue();
	}

	@Test
	void lookupReturnsOnlyPublishedSeedBooks() {
		Map<UUID, CatalogBook> books = gateway.lookup(List.of(PUBLISHED_1, DRAFT, PUBLISHED_2));

		assertThat(books).containsOnlyKeys(PUBLISHED_1, PUBLISHED_2);
	}

}
