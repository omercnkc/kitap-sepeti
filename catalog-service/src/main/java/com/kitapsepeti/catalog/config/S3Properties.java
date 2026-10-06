package com.kitapsepeti.catalog.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO / S3 kapak depolama ({@code app.s3.*}).
 * <p>
 * {@code endpoint}: PutObject (container içi {@code http://minio:9000}). {@code publicBaseUrl}: DB
 * {@code cover_url} ve tarayıcı ({@code http://localhost:9000}); asla {@code minio:9000} yazılmaz.
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
		String endpoint,
		String accessKey,
		String secretKey,
		String bucket,
		String publicBaseUrl,
		String region,
		long maxObjectBytes,
		Duration ingestConnectTimeout,
		Duration ingestReadTimeout,
		List<String> allowedIngestHosts) {

	public static final long DEFAULT_MAX_OBJECT_BYTES = 5_242_880L;

	public S3Properties {
		if (region == null || region.isBlank()) {
			region = "us-east-1";
		}
		if (maxObjectBytes <= 0) {
			maxObjectBytes = DEFAULT_MAX_OBJECT_BYTES;
		}
		if (ingestConnectTimeout == null) {
			ingestConnectTimeout = Duration.ofSeconds(3);
		}
		if (ingestReadTimeout == null) {
			ingestReadTimeout = Duration.ofSeconds(5);
		}
		if (allowedIngestHosts == null || allowedIngestHosts.isEmpty()) {
			allowedIngestHosts = List.of("covers.openlibrary.org");
		}
		else {
			allowedIngestHosts = List.copyOf(allowedIngestHosts);
		}
	}

}
