package com.kitapsepeti.catalog.config;

import com.kitapsepeti.catalog.security.JwtProperties;
import com.kitapsepeti.common.security.JwkSetJwtDecoders;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * user-service'in yayınladığı JWT'leri doğrular; Catalog token üretmez, özel anahtarı yoktur.
 * Ayrıntılar (JWKS önbelleği, RS256, iss/exp, 2 sn/3 sn zaman aşımı): {@link JwkSetJwtDecoders}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfig {

	@Bean
	public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties resourceServerProperties, JwtProperties jwtProperties) {
		return JwkSetJwtDecoders.rs256(resourceServerProperties.getJwt().getJwkSetUri(), jwtProperties.issuer());
	}

}
