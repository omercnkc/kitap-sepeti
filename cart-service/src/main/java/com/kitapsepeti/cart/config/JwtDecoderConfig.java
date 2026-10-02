package com.kitapsepeti.cart.config;

import com.kitapsepeti.cart.security.JwtProperties;
import com.kitapsepeti.cart.security.JwtSubjects;
import com.kitapsepeti.common.security.JwkSetJwtDecoders;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfig {

	/**
	 * İmza/issuer/süre common'daki RS256 decoder'da; sepet ayrıca {@code sub}'ın kullanıcı UUID'si olmasını şart koşar.
	 * {@link BadJwtException} şart: başka bir JwtException 401 değil 503 (AUTHENTICATION_UNAVAILABLE) olurdu.
	 */
	@Bean
	public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties resourceServerProperties, JwtProperties jwtProperties) {
		JwtDecoder delegate = JwkSetJwtDecoders.rs256(resourceServerProperties.getJwt().getJwkSetUri(),
				jwtProperties.issuer());
		return token -> {
			Jwt jwt = delegate.decode(token);
			if (JwtSubjects.userId(jwt.getSubject()).isEmpty()) {
				throw new BadJwtException("JWT subject is not a user id");
			}
			return jwt;
		};
	}

}
