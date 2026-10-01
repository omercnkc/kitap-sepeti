package com.kitapsepeti.common.security.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Yalnızca common testlerinde geçerli anahtar; özeti test içinde hesaplanır. */
final class TestKeys {

	static final String ORDER_SERVICE_KEY = "test-only-common-order-service-key-3f9d1c2a";

	static final String ORDER_SERVICE_KEY_SHA256 = sha256Hex(ORDER_SERVICE_KEY);

	private TestKeys() {
	}

	static String sha256Hex(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
