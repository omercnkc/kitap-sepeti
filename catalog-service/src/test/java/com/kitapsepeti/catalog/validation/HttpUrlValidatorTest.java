package com.kitapsepeti.catalog.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Spring'siz birim testleri: kapak URL'si yalnızca mutlak http/https. */
class HttpUrlValidatorTest {

	private final HttpUrlValidator validator = new HttpUrlValidator();

	@ParameterizedTest
	@ValueSource(strings = { "http://cdn.example.com/kapak.jpg", "https://cdn.example.com/a/b.png?w=200",
			"HTTPS://CDN.EXAMPLE.COM/x.webp" })
	void acceptsAbsoluteHttpUrls(String url) {
		assertThat(validator.isValid(url, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ftp://cdn.example.com/kapak.jpg", "javascript:alert(1)", "file:///etc/passwd",
			"/kapak.jpg", "cdn.example.com/kapak.jpg", "http://", "https:///yol", "kapak url" })
	void rejectsOtherSchemesRelativeAndMalformed(String url) {
		assertThat(validator.isValid(url, null)).isFalse();
	}

	@Test
	void nullAndEmptyAreValidForOptionalField() {
		assertThat(validator.isValid(null, null)).isTrue();
		assertThat(validator.isValid("", null)).isTrue();
	}

}
