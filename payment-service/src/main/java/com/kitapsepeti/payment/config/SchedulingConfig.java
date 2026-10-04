package com.kitapsepeti.payment.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zamanlanmış işler: outbox worker ve mock kurtarma görevi. İkisi de kapalıyken scheduler açılmaz. Mock webhook'un
 * gecikmeli gönderimi bunu kullanmaz (kendi zamanlayıcısı var).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Conditional(SchedulingConfig.AnyJobEnabled.class)
public class SchedulingConfig {

	static class AnyJobEnabled extends AnyNestedCondition {

		AnyJobEnabled() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
		static class OutboxEnabled {
		}

		@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
		@ConditionalOnProperty(name = "app.payment.mock.recovery.enabled", havingValue = "true", matchIfMissing = true)
		static class MockRecoveryEnabled {
		}

	}

}
