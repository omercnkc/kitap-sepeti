package com.kitapsepeti.user.controller;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token doğrulayacak diğer servisler için açık anahtarları JWKS (RFC 7517) formatında yayınlar.
 * Yalnızca açık anahtar parçaları ({@code n}, {@code e}) döner; özel anahtar asla dışarı çıkmaz.
 */
@RestController
public class JwksController {

	private final JWKSet jwkSet;

	public JwksController(JWKSet jwkSet) {
		this.jwkSet = jwkSet;
	}

	@GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> jwks() {
		return jwkSet.toPublicJWKSet().toJSONObject();
	}

}
