package com.kitapsepeti.user.controller;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token doğrulayacak diğer servisler için açık anahtarları JWKS (RFC 7517) formatında yayınlar.
 * Yalnızca açık anahtar parçaları ({@code n}, {@code e}) döner; özel anahtar asla dışarı çıkmaz.
 */
@RestController
@Tag(name = "Keys", description = "Access token imzasını doğrulamak için açık anahtarlar.")
@SecurityRequirements
public class JwksController {

	private final JWKSet jwkSet;

	public JwksController(JWKSet jwkSet) {
		this.jwkSet = jwkSet;
	}

	@GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "getJwks", summary = "JWKS",
			description = "RS256 açık anahtar(lar)ı. Token başlığındaki `kid` ile eşleşen anahtar seçilir; "
					+ "anahtar değişince `kid` de değişir.")
	@ApiResponse(responseCode = "200", description = "Açık anahtar kümesi.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
					{"keys":[{"kty":"RSA","e":"AQAB","use":"sig","kid":"hN3x...","alg":"RS256","n":"0vx7..."}]}""")))
	public Map<String, Object> jwks() {
		return jwkSet.toPublicJWKSet().toJSONObject();
	}

}
