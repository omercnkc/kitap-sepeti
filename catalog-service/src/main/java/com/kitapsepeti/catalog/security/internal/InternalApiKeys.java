package com.kitapsepeti.catalog.security.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Yapılandırılmış istemci özetleri; açılışta doğrulanır (fail-fast). Hata mesajları istemci adını ve özelliği
 * içerir, değeri ASLA içermez. Eşleştirme sabit zamanlı karşılaştırmayla ({@link MessageDigest#isEqual}) ve
 * her zaman tüm istemciler gezilerek yapılır.
 */
public final class InternalApiKeys {

	private static final Logger log = LoggerFactory.getLogger(InternalApiKeys.class);

	private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

	private final List<EnabledClient> clients;

	public InternalApiKeys(InternalAuthProperties properties) {
		List<EnabledClient> enabled = new ArrayList<>();
		Set<String> names = new HashSet<>();
		List<InternalAuthProperties.Client> configured = properties.clients();
		for (int i = 0; i < configured.size(); i++) {
			InternalAuthProperties.Client client = configured.get(i);
			String property = "app.internal-auth.clients[" + i + "]";
			if (client.name() == null || client.name().isBlank()) {
				throw new IllegalStateException(property + ".name must not be blank");
			}
			if (!names.add(client.name())) {
				throw new IllegalStateException(property + ".name '" + client.name() + "' is configured more than once");
			}
			if (!client.enabled()) {
				log.info("Internal client '{}' is disabled (no key hash configured)", client.name());
				continue;
			}
			if (!SHA256_HEX.matcher(client.keySha256().trim()).matches()) {
				throw new IllegalStateException(property + ".key-sha256 of internal client '" + client.name()
						+ "' must be a 64-character hex SHA-256 digest of the API key (configured value not shown)");
			}
			enabled.add(new EnabledClient(client.name(), HexFormat.of().parseHex(client.keySha256().trim())));
		}
		this.clients = List.copyOf(enabled);
	}

	/** @return anahtar bir istemciye aitse istemci adı */
	public Optional<String> clientFor(String rawKey) {
		byte[] digest = sha256(rawKey);
		String match = null;
		for (EnabledClient client : this.clients) {
			if (MessageDigest.isEqual(digest, client.keyHash()) && match == null) {
				match = client.name();
			}
		}
		return Optional.ofNullable(match);
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private record EnabledClient(String name, byte[] keyHash) {
	}

}
