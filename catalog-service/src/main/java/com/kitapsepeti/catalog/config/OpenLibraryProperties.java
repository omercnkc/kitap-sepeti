package com.kitapsepeti.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openlibrary")
public record OpenLibraryProperties(
		String baseUrl,
		Duration connectTimeout,
		Duration readTimeout) {

	public OpenLibraryProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://openlibrary.org";
		}
		if (connectTimeout == null) {
			connectTimeout = Duration.ofSeconds(3);
		}
		if (readTimeout == null) {
			readTimeout = Duration.ofSeconds(5);
		}
	}

}
