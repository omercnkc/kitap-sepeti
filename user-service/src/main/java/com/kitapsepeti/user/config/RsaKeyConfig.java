package com.kitapsepeti.user.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

import com.kitapsepeti.user.security.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 JWT anahtarları: PEM dosyalarından RSA anahtar çiftini yükler; token imzalama
 * ({@link JwtEncoder}), doğrulama ({@link JwtDecoder}) ve JWKS yayını ({@link JWKSet}) için bean'ler üretir.
 * {@code kid}, açık anahtarın RFC 7638 thumbprint'idir; anahtar değişince kendiliğinden değişir.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class RsaKeyConfig {

	@Bean
	public RSAKey rsaKey(JwtProperties properties) {
		RSAPublicKey publicKey = read(properties.publicKeyLocation(), RsaKeyConverters.x509());
		RSAPrivateKey privateKey = read(properties.privateKeyLocation(), RsaKeyConverters.pkcs8());
		if (!publicKey.getModulus().equals(privateKey.getModulus())) {
			throw new IllegalStateException("JWT public key does not belong to the configured private key");
		}
		try {
			return new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyIDFromThumbprint()
				.build();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Cannot compute JWK thumbprint", ex);
		}
	}

	/** Özel anahtarı da içerir; dışarıya yalnızca {@link JWKSet#toPublicJWKSet()} verilmeli. */
	@Bean
	public JWKSet jwkSet(RSAKey rsaKey) {
		return new JWKSet(rsaKey);
	}

	@Bean
	public JwtEncoder jwtEncoder(JWKSet jwkSet) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
	}

	/** Yalnızca RS256 kabul eder; imza, {@code iss} ve {@code exp}/{@code nbf} doğrulanır (saat: {@link Clock} bean'i). */
	@Bean
	public JwtDecoder jwtDecoder(RSAKey rsaKey, JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder;
		try {
			decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Cannot extract RSA public key", ex);
		}
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
		timestampValidator.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				timestampValidator, new JwtIssuerValidator(properties.issuer())));
		return decoder;
	}

	private static <K> K read(Resource resource, Converter<InputStream, K> converter) {
		try (InputStream in = resource.getInputStream()) {
			return converter.convert(in);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot read JWT key from " + resource, ex);
		}
	}

}
