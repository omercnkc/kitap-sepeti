package com.kitapsepeti.catalog.config;

import java.net.URI;

import com.kitapsepeti.catalog.storage.CoverStorage;
import com.kitapsepeti.catalog.storage.InMemoryCoverStorage;
import com.kitapsepeti.catalog.storage.S3CoverStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

	@Bean
	@Profile("!test")
	S3Client s3Client(S3Properties properties) {
		return S3Client.builder()
			.endpointOverride(URI.create(trimTrailingSlash(properties.endpoint())))
			.region(Region.of(properties.region()))
			.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.build();
	}

	@Bean
	@Profile("!test")
	CoverStorage s3CoverStorage(S3Client s3Client, S3Properties properties) {
		return new S3CoverStorage(s3Client, properties);
	}

	@Bean
	@Profile("test")
	CoverStorage inMemoryCoverStorage(S3Properties properties) {
		return new InMemoryCoverStorage(properties);
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return value;
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

}
