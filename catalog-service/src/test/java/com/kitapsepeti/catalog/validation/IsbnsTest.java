package com.kitapsepeti.catalog.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Spring'siz birim testleri: ISBN normalizasyonu, checksum ve {@link IsbnValidator}. */
class IsbnsTest {

	private final IsbnValidator validator = new IsbnValidator();

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"978-605-360-077-0 | 9786053600770",
			"978 0 306 40615 7 | 9780306406157",
			"0-306-40615-2     | 0306406152",
			"0-8044-2957-x     | 080442957X",
			"080442957X        | 080442957X" })
	void normalizesAndAcceptsValidIsbns(String raw, String normalized) {
		assertThat(Isbns.normalize(raw)).isEqualTo(normalized);
		assertThat(Isbns.isValid(normalized)).isTrue();
		assertThat(validator.isValid(raw, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"978-605-360-077-1", // ISBN-13 checksum yanlış
			"0-306-40615-3", // ISBN-10 checksum yanlış
			"X804429570", // X yalnızca ISBN-10'un son hanesi olabilir
			"978030640615X", // ISBN-13'te X olmaz
			"97803064061", // uzunluk 11
			"978-0-306-40615-77", // uzunluk 14
			"abcdefghij",
			"\u0669\u0667\u0668\u0660\u0663\u0660\u0666\u0664\u0660\u0666\u0661\u0665\u0667", // Arapça-Hint rakamları
			"   " })
	void rejectsInvalidIsbns(String raw) {
		assertThat(Isbns.isValid(Isbns.normalize(raw))).isFalse();
		assertThat(validator.isValid(raw, null)).isFalse();
	}

	@Test
	void nullAndEmptyAreValidForOptionalField() {
		assertThat(validator.isValid(null, null)).isTrue();
		assertThat(validator.isValid("", null)).isTrue();
		assertThat(Isbns.normalize(null)).isNull();
		assertThat(Isbns.isValid(null)).isFalse();
	}

}
