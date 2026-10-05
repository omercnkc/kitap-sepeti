package com.kitapsepeti.catalog.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenLibraryProperties.class)
public class OpenLibraryConfig {

	@Bean
	RestClient openLibraryRestClient(OpenLibraryProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		Duration readTimeout = properties.readTimeout();
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
			.baseUrl(trimTrailingSlash(properties.baseUrl()))
			.requestFactory(requestFactory)
			.build();
	}

	private static String trimTrailingSlash(String baseUrl) {
		if (baseUrl.endsWith("/")) {
			return baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl;
	}

}
