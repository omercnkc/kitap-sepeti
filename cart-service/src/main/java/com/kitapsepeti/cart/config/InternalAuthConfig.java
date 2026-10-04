package com.kitapsepeti.cart.config;

import com.kitapsepeti.common.security.internal.InternalApiKeys;
import com.kitapsepeti.common.security.internal.InternalAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code app.internal-auth} istemcileri (catalog ve payment ile aynı property'ler ve aynı politika, common
 * {@link InternalApiKeys}): özet yok, boş ya da 64 hex değilse uygulama açılmaz; hata mesajları değeri içermez.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthConfig {

	@Bean
	public InternalApiKeys internalApiKeys(InternalAuthProperties properties) {
		return new InternalApiKeys(properties);
	}

}
