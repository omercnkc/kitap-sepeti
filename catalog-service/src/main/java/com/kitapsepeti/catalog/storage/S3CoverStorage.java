package com.kitapsepeti.catalog.storage;

import com.kitapsepeti.catalog.config.S3Properties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * MinIO / S3 üzerinde kapak nesnesi. PutObject {@code endpoint} üzerinden; URL {@code publicBaseUrl}.
 */
public final class S3CoverStorage implements CoverStorage {

	private final S3Client s3Client;

	private final S3Properties properties;

	private final String publicPrefix;

	public S3CoverStorage(S3Client s3Client, S3Properties properties) {
		this.s3Client = s3Client;
		this.properties = properties;
		this.publicPrefix = trimTrailingSlash(properties.publicBaseUrl()) + "/" + properties.bucket() + "/";
	}

	@Override
	public String put(byte[] data, String contentType, String objectKey) {
		PutObjectRequest request = PutObjectRequest.builder()
			.bucket(this.properties.bucket())
			.key(objectKey)
			.contentType(contentType)
			.build();
		this.s3Client.putObject(request, RequestBody.fromBytes(data));
		return toPublicUrl(objectKey);
	}

	@Override
	public void delete(String objectKey) {
		if (objectKey == null || objectKey.isBlank()) {
			return;
		}
		this.s3Client.deleteObject(DeleteObjectRequest.builder()
			.bucket(this.properties.bucket())
			.key(objectKey)
			.build());
	}

	@Override
	public boolean isManagedPublicUrl(String url) {
		return url != null && url.startsWith(this.publicPrefix);
	}

	@Override
	public String toPublicUrl(String objectKey) {
		return this.publicPrefix + objectKey;
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

}
