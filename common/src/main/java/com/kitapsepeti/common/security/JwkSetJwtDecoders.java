package com.kitapsepeti.common.security;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/**
 * user-service'in yayınladığı JWT'leri başka bir servisin doğrulaması için JwtDecoder; token üretmez, özel anahtar yoktur.
 * Açık anahtar {@code jwk-set-uri}'den ilk doğrulamada çekilir ve önbelleğe alınır (açılışta istek atılmaz;
 * user-service kapalıyken de servis ayağa kalkar). Kabul edilen tek algoritma RS256; {@code kid} JWKS'ten seçilir,
 * bilinmeyen {@code kid} önbelleği bir kez yeniler. Doğrulayıcılar: exp/nbf (60 sn tolerans) ve {@code iss}.
 */
public final class JwkSetJwtDecoders {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

	private JwkSetJwtDecoders() {
	}

	public static JwtDecoder rs256(String jwkSetUri, String issuer) {
		Assert.hasText(jwkSetUri, "spring.security.oauth2.resourceserver.jwt.jwk-set-uri must be set");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.restOperations(jwksClient())
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
		return decoder;
	}

	/** Varsayılan RestTemplate'in süresi sınırsız; user-service yanıt vermezse istekler asılı kalmasın. */
	private static RestTemplate jwksClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return new RestTemplate(requestFactory);
	}

}
