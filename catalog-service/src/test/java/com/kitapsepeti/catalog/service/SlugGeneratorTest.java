package com.kitapsepeti.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlugGeneratorTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Çağdaş Öykü Yayınları | cagdas-oyku-yayinlari",
			"İSTANBUL Işık         | istanbul-isik",
			"'  Çok   Boşluk  '    | cok-bosluk",
			"C++ & Java            | c-java",
			"ÇĞİÖŞÜ çğıöşü         | cgiosu-cgiosu",
			"Gabriel García Márquez | gabriel-garcia-marquez",
			"1984                  | 1984",
			"--Zaten-slug--        | zaten-slug" })
	void generatesSlugFromName(String name, String expected) {
		assertThat(SlugGenerator.fromName(name)).isEqualTo(expected).matches("^[a-z0-9]+(-[a-z0-9]+)*$");
	}

	@ParameterizedTest
	@ValueSource(strings = { "!!!", "   ", "", "—…" })
	void nameWithoutLettersOrDigitsGivesEmptySlug(String name) {
		assertThat(SlugGenerator.fromName(name)).isEmpty();
	}

}
