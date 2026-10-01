package com.kitapsepeti.catalog.config;

import java.time.Duration;

import com.kitapsepeti.catalog.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/**
 * user-service'in yayınladığı JWT'leri doğrular; Catalog token üretmez, özel anahtarı yoktur.
 * Açık anahtar {@code jwk-set-uri}'den ilk doğrulamada çekilir ve önbelleğe alınır (açılışta istek atılmaz;
 * user-service kapalıyken de servis ayağa kalkar). Kabul edilen tek algoritma RS256; {@code kid} JWKS'ten seçilir,
 * bilinmeyen {@code kid} önbelleği bir kez yeniler. Doğrulayıcılar: exp/nbf (60 sn tolerans) ve {@code iss}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfig {

	@Bean
	public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties resourceServerProperties, JwtProperties jwtProperties) {
		String jwkSetUri = resourceServerProperties.getJwt().getJwkSetUri();
		Assert.hasText(jwkSetUri, "spring.security.oauth2.resourceserver.jwt.jwk-set-uri must be set");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.restOperations(jwksClient())
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()));
		return decoder;
	}

	/** Varsayılan RestTemplate'in süresi sınırsız; user-service yanıt vermezse istekler asılı kalmasın. */
	private static RestTemplate jwksClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(3));
		return new RestTemplate(requestFactory);
	}

}
