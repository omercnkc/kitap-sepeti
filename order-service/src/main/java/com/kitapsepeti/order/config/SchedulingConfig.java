package com.kitapsepeti.order.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zamanlanmış işler: outbox worker, stock sync ve bekleyen sipariş uzlaştırma görevleri. Hepsi kapalıyken scheduler
 * açılmaz.
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

		@ConditionalOnProperty(name = "app.stock-sync.enabled", havingValue = "true", matchIfMissing = true)
		static class StockSyncEnabled {
		}

		@ConditionalOnProperty(name = "app.pending-reconcile.enabled", havingValue = "true", matchIfMissing = true)
		static class PendingReconcileEnabled {
		}

	}

}
