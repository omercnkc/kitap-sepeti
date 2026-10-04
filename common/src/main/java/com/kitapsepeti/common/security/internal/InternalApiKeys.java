package com.kitapsepeti.common.security.internal;

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

/**
 * Yapılandırılmış istemci özetleri; açılışta doğrulanır (fail-fast). Tek politika (her servis için aynı): en az bir
 * istemci olmalı ve her istemcinin {@code key-sha256}'sı 64 karakter hex olmalı; yok, boş, yalnızca boşluk ya da
 * bozuk özet uygulamayı açmaz ("kapalı istemci" kavramı yoktur). Hata mesajları istemci adını ve özelliği içerir,
 * değeri ASLA içermez. Eşleştirme sabit zamanlı karşılaştırmayla ({@link MessageDigest#isEqual}) ve her zaman tüm
 * istemciler gezilerek yapılır.
 */
public final class InternalApiKeys {

	private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

	private final List<Client> clients;

	public InternalApiKeys(InternalAuthProperties properties) {
		List<InternalAuthProperties.Client> configured = properties.clients();
		if (configured.isEmpty()) {
			throw new IllegalStateException("app.internal-auth.clients must configure at least one internal client");
		}
		List<Client> clients = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (int i = 0; i < configured.size(); i++) {
			InternalAuthProperties.Client client = configured.get(i);
			String property = "app.internal-auth.clients[" + i + "]";
			if (client.name() == null || client.name().isBlank()) {
				throw new IllegalStateException(property + ".name must not be blank");
			}
			if (!names.add(client.name())) {
				throw new IllegalStateException(property + ".name '" + client.name() + "' is configured more than once");
			}
			String keySha256 = (client.keySha256() != null) ? client.keySha256().trim() : "";
			if (keySha256.isEmpty()) {
				throw new IllegalStateException(property + ".key-sha256 of internal client '" + client.name()
						+ "' must be set to the 64-character hex SHA-256 digest of the API key");
			}
			if (!SHA256_HEX.matcher(keySha256).matches()) {
				throw new IllegalStateException(property + ".key-sha256 of internal client '" + client.name()
						+ "' must be a 64-character hex SHA-256 digest of the API key (configured value not shown)");
			}
			clients.add(new Client(client.name(), HexFormat.of().parseHex(keySha256)));
		}
		this.clients = List.copyOf(clients);
	}

	/** @return anahtar bir istemciye aitse istemci adı */
	public Optional<String> clientFor(String rawKey) {
		byte[] digest = sha256(rawKey);
		String match = null;
		for (Client client : this.clients) {
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

	private record Client(String name, byte[] keyHash) {
	}

}
