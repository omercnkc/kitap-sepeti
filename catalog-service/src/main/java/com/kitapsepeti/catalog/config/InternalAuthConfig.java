package com.kitapsepeti.catalog.config;

import com.kitapsepeti.common.security.internal.InternalApiKeys;
import com.kitapsepeti.common.security.internal.InternalAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code app.internal-auth} istemcileri (cart ve payment ile aynı property'ler ve aynı politika, common
 * {@link InternalApiKeys}): özet yok, boş ya da 64 hex değilse uygulama açılmaz ("boş özet = istemci kapalı" artık
 * yok); hata mesajları değeri içermez.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthConfig {

	@Bean
	public InternalApiKeys internalApiKeys(InternalAuthProperties properties) {
		return new InternalApiKeys(properties);
	}

}
