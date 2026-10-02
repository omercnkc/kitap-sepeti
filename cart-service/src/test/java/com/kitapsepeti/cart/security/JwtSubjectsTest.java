package com.kitapsepeti.cart.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtSubjectsTest {

	@Test
	void canonicalUuidIsAccepted() {
		UUID id = UUID.randomUUID();

		assertThat(JwtSubjects.userId(id.toString())).contains(id);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "not-a-uuid", "1-1-1-1-1", "123E4567-E89B-12D3-A456-426614174000",
			" 123e4567-e89b-12d3-a456-426614174000", "123e4567e89b12d3a456426614174000" })
	void anythingElseIsRejected(String subject) {
		assertThat(JwtSubjects.userId(subject)).isEmpty();
	}

}
