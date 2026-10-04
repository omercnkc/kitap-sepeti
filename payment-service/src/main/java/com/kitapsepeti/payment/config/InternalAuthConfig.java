package com.kitapsepeti.payment.config;

import java.util.List;

import com.kitapsepeti.common.security.internal.InternalApiKeys;
import com.kitapsepeti.common.security.internal.InternalAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code app.internal-auth} istemcileri (catalog/cart ile aynı property'ler; özet biçimi {@link InternalApiKeys}'te
 * doğrulanır). cart ile aynı politika: özeti boş istemci "kapalı" sayılmaz, uygulama açılmaz. Ödemenin tek kullanıcısı
 * Order; anahtarsız açılan bir payment her ödeme isteğini 401 ile düşürürdü. Hata mesajları değeri içermez.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthConfig {

	@Bean
	public InternalApiKeys internalApiKeys(InternalAuthProperties properties) {
		InternalApiKeys keys = new InternalApiKeys(properties);
		requireEveryClientEnabled(properties.clients());
		return keys;
	}

	private static void requireEveryClientEnabled(List<InternalAuthProperties.Client> clients) {
		if (clients.isEmpty()) {
			throw new IllegalStateException("app.internal-auth.clients must configure at least one internal client");
		}
		for (int i = 0; i < clients.size(); i++) {
			InternalAuthProperties.Client client = clients.get(i);
			if (!client.enabled()) {
				throw new IllegalStateException("app.internal-auth.clients[" + i + "].key-sha256 of internal client '"
						+ client.name() + "' must be set; payment-service does not start with a disabled internal client");
			}
		}
	}

}
