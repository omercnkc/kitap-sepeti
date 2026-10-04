package com.kitapsepeti.order.config;

import com.kitapsepeti.order.client.CircuitBreakerProperties;
import com.kitapsepeti.order.client.InternalApiKey;
import com.kitapsepeti.order.client.InternalClientConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cart, Catalog ve Payment Feign istemcileri ({@code client} paketi ve alt paketleri). Adresler
 * {@code app.clients.*.base-url}, zaman aşımları ve log düzeyi {@code spring.cloud.openfeign.client.config.*}.
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackageClasses = InternalClientConfiguration.class)
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class ClientConfig {

	/** Yok/boşsa uygulama açılmaz (internal çağrılar ancak 401 alırdı). */
	@Bean
	InternalApiKey internalApiKey(@Value("${app.clients.internal-api-key:}") String raw) {
		return InternalApiKey.of(raw);
	}

}
