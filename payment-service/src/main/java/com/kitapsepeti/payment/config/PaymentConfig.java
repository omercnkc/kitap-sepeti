package com.kitapsepeti.payment.config;

import java.time.Clock;

import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.MockPaymentProvider;
import com.kitapsepeti.payment.provider.PaymentProvider;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import com.kitapsepeti.payment.provider.mock.MockWebhookVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig {

	/** Tek sağlayıcı bean'i; henüz uygulanmamış bir sağlayıcı seçilirse uygulama açılmaz. */
	@Bean
	public PaymentProvider paymentProvider(PaymentProperties properties) {
		return switch (properties.provider()) {
			case MOCK -> new MockPaymentProvider();
			case IYZICO, PAYTR, STRIPE -> throw new IllegalStateException(
					"Payment provider '" + properties.provider().dbValue() + "' is not supported yet; use 'mock'");
		};
	}

	@Bean
	public MockOutcomeRule mockOutcomeRule(PaymentProperties properties) {
		return new MockOutcomeRule(properties.mock().failCents());
	}

	/**
	 * Mock tek desteklenen sağlayıcı olduğu için webhook secret'ı her zaman zorunlu: yok, boş ya da kısaysa uygulama
	 * açılmaz (imzasız webhook'u kabul eden ya da her webhook'u reddeden bir servis yerine).
	 */
	@Bean
	public MockWebhookSigner mockWebhookSigner(PaymentProperties properties) {
		return new MockWebhookSigner(properties.mock().webhookSecret());
	}

	@Bean
	public MockWebhookVerifier mockWebhookVerifier(MockWebhookSigner signer, PaymentProperties properties,
			Clock clock) {
		return new MockWebhookVerifier(signer, properties.webhook().tolerance(), clock);
	}

}
