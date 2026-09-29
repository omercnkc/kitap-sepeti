package com.kitapsepeti.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

import com.kitapsepeti.user.TestcontainersConfiguration;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JwksControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publishesOnlyPublicRsaKeyWithoutToken() throws Exception {
		mockMvc.perform(get("/.well-known/jwks.json"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.keys.length()").value(1))
			.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
			.andExpect(jsonPath("$.keys[0].alg").value("RS256"))
			.andExpect(jsonPath("$.keys[0].use").value("sig"))
			.andExpect(jsonPath("$.keys[0].kid").value(testKeyThumbprint()))
			.andExpect(jsonPath("$.keys[0].n").isNotEmpty())
			.andExpect(jsonPath("$.keys[0].e").isNotEmpty())
			.andExpect(jsonPath("$.keys[0].d").doesNotExist())
			.andExpect(jsonPath("$.keys[0].p").doesNotExist())
			.andExpect(jsonPath("$.keys[0].q").doesNotExist())
			.andExpect(jsonPath("$.keys[0].dp").doesNotExist())
			.andExpect(jsonPath("$.keys[0].dq").doesNotExist())
			.andExpect(jsonPath("$.keys[0].qi").doesNotExist());
	}

	/** kid'in test anahtarına ait olması, testin .env'deki gerçek anahtarı kullanmadığını da kanıtlar. */
	private static String testKeyThumbprint() throws Exception {
		try (InputStream in = new ClassPathResource("jwt/test-public.pem").getInputStream()) {
			RSAPublicKey publicKey = RsaKeyConverters.x509().convert(in);
			return new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
		}
	}

}
