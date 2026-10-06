package com.kitapsepeti.catalog.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuthorNamesTest {

	@Test
	void compactTrimsDedupesWithTurkishCaseAndDropsBlanks() {
		assertThat(AuthorNames.compact(Arrays.asList("  Ahmet  ", "ahmet", "  ", "Ayşe", "AYŞE", null)))
			.containsExactly("Ahmet", "Ayşe");
	}

	@Test
	void compactNullOrEmptyIsEmpty() {
		assertThat(AuthorNames.compact(null)).isEmpty();
		assertThat(AuthorNames.compact(List.of())).isEmpty();
	}

}
