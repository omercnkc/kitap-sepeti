package com.kitapsepeti.payment.config;

import java.time.Clock;

import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.mock.MockRecoveryJob;
import com.kitapsepeti.payment.provider.mock.MockWebhookDispatcher;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import com.kitapsepeti.payment.repository.PaymentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mock sağlayıcının kendi webhook ucuna gönderimi ve kurtarma görevi; yalnızca {@code app.payment.provider=mock}.
 * Gönderici her zaman vardır (kurtarma görevi kullanır); otomatik gönderim {@code app.payment.mock.dispatch.enabled},
 * kurtarma görevi {@code app.payment.mock.recovery.enabled} ile kapatılır.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockWebhookConfig {

	@Bean
	public MockWebhookDispatcher mockWebhookDispatcher(PaymentRepository payments, MockOutcomeRule outcomeRule,
			MockWebhookSigner signer, JsonMapper jsonMapper, Clock clock, PaymentProperties properties) {
		return new MockWebhookDispatcher(payments, outcomeRule, signer, jsonMapper, clock, properties.mock());
	}

	@Bean
	@ConditionalOnProperty(name = "app.payment.mock.recovery.enabled", havingValue = "true", matchIfMissing = true)
	public MockRecoveryJob mockRecoveryJob(PaymentRepository payments, MockWebhookDispatcher dispatcher, Clock clock,
			PaymentProperties properties) {
		return new MockRecoveryJob(payments, dispatcher, clock, properties.mock().recovery());
	}

}
