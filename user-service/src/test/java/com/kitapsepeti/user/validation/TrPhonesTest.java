package com.kitapsepeti.user.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TrPhonesTest {

	@ParameterizedTest
	@ValueSource(strings = { "5551234567", "05551234567", "+905551234567", "90 555 123 45 67", " 555 123 45 67 " })
	void acceptsAndNormalizesValidMobiles(String raw) {
		assertThat(TrPhones.isValidOptional(raw)).isTrue();
		assertThat(TrPhones.toCanonicalOrNull(raw)).isEqualTo("5551234567");
	}

	@ParameterizedTest
	@ValueSource(strings = { "123", "abcdef", "055512345", "4551234567", "55512345678", "+902121234567" })
	void rejectsInvalid(String raw) {
		assertThat(TrPhones.isValidOptional(raw)).isFalse();
		assertThat(TrPhones.toCanonicalOrNull(raw)).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void blankIsValidOptional(String raw) {
		assertThat(TrPhones.isValidOptional(raw)).isTrue();
		assertThat(TrPhones.toCanonicalOrNull(raw)).isNull();
	}

	@Test
	void canonicalCheck() {
		assertThat(TrPhones.isCanonical("5551112233")).isTrue();
		assertThat(TrPhones.isCanonical("05551112233")).isFalse();
	}

}
