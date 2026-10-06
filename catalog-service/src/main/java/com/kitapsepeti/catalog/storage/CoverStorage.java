package com.kitapsepeti.catalog.storage;

/**
 * Kitap kapak nesneleri. Public URL = {@code publicBaseUrl/bucket/key} (DB ve tarayıcı için).
 */
public interface CoverStorage {

	/** Nesneyi yazar; dönen değer yönetilen public URL'dir. */
	String put(byte[] data, String contentType, String objectKey);

	/** Yoksa no-op. */
	default void delete(String objectKey) {
	}

	/** URL bu deponun public tabanına mı ait? */
	boolean isManagedPublicUrl(String url);

	String toPublicUrl(String objectKey);

}
