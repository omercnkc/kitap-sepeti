package com.kitapsepeti.catalog.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.kitapsepeti.catalog.config.S3Properties;

/**
 * Test profili için bellek içi depo (MinIO container gerekmez).
 */
public final class InMemoryCoverStorage implements CoverStorage {

	private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

	private final String publicPrefix;

	public InMemoryCoverStorage(S3Properties properties) {
		this.publicPrefix = trimTrailingSlash(properties.publicBaseUrl()) + "/" + properties.bucket() + "/";
	}

	@Override
	public String put(byte[] data, String contentType, String objectKey) {
		this.objects.put(objectKey, new StoredObject(data, contentType));
		return toPublicUrl(objectKey);
	}

	@Override
	public void delete(String objectKey) {
		if (objectKey != null) {
			this.objects.remove(objectKey);
		}
	}

	@Override
	public boolean isManagedPublicUrl(String url) {
		return url != null && url.startsWith(this.publicPrefix);
	}

	@Override
	public String toPublicUrl(String objectKey) {
		return this.publicPrefix + objectKey;
	}

	/** Test doğrulaması için. */
	public boolean containsKey(String objectKey) {
		return this.objects.containsKey(objectKey);
	}

	public int size() {
		return this.objects.size();
	}

	/** Testler arası sızıntıyı önlemek için. */
	public void clear() {
		this.objects.clear();
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private record StoredObject(byte[] data, String contentType) {
	}

}
