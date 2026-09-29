package com.kitapsepeti.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

	private final PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

	@Test
	void encodesSamePasswordToDifferentHashesThatBothMatch() {
		String first = passwordEncoder.encode("Parola123!");
		String second = passwordEncoder.encode("Parola123!");

		assertThat(first).isNotEqualTo(second);
		assertThat(passwordEncoder.matches("Parola123!", first)).isTrue();
		assertThat(passwordEncoder.matches("Parola123!", second)).isTrue();
	}

	@Test
	void producesBcryptHashWithStrength10() {
		String hash = passwordEncoder.encode("Parola123!");

		assertThat(hash).startsWith("$2a$10$").hasSize(60);
	}

	@Test
	void rejectsWrongPassword() {
		String hash = passwordEncoder.encode("Parola123!");

		assertThat(passwordEncoder.matches("YanlisParola", hash)).isFalse();
	}

}
