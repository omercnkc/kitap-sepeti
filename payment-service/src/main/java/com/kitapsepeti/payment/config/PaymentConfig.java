package com.kitapsepeti.payment.config;

import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.MockPaymentProvider;
import com.kitapsepeti.payment.provider.PaymentProvider;
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

}
