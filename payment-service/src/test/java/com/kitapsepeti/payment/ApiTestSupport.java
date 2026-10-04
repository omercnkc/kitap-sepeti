package com.kitapsepeti.payment;

import com.kitapsepeti.payment.provider.PaymentProvider;
import com.kitapsepeti.payment.repository.PaymentRepository;
import com.kitapsepeti.payment.support.InternalTestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tam uygulama bağlamı açan testlerin ortak tabanı: MockMvc, Testcontainers MySQL ve test JVM'inde üretilen internal
 * anahtar ({@link InternalTestKeys}). Bütün alt sınıflar aynı bağlamı ve aynı konteyneri paylaşır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	/** Gerçek mock sağlayıcı; testler çağrıları sayar ya da hata taklit eder (her testten sonra sıfırlanır). */
	@MockitoSpyBean
	protected PaymentProvider provider;

	/** Gerçek repository; yarış testleri tek tek metotları geciktirir (her testten sonra sıfırlanır). */
	@MockitoSpyBean
	protected PaymentRepository payments;

	@DynamicPropertySource
	static void internalKeys(DynamicPropertyRegistry registry) {
		InternalTestKeys.register(registry);
	}

	@BeforeEach
	void resetTables() {
		jdbc.update("DELETE FROM provider_events");
		jdbc.update("DELETE FROM payments");
	}

}
