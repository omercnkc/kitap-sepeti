package com.kitapsepeti.common.security.internal;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Servisler arası istemciler ({@code app.internal-auth.clients}). Ham anahtar burada YOKTUR; yalnızca SHA-256
 * özeti (64 karakter hex) tutulur. Özet zorunludur; biçim doğrulaması bağlama sırasında değil {@link InternalApiKeys}'te
 * yapılır, böylece hata mesajı yanlışlıkla girilmiş bir değeri (ör. ham anahtar) hiçbir zaman yansıtmaz.
 */
@ConfigurationProperties(prefix = "app.internal-auth")
public record InternalAuthProperties(List<Client> clients) {

	public InternalAuthProperties {
		clients = (clients == null) ? List.of() : List.copyOf(clients);
	}

	/**
	 * @param name Authentication principal'ı ve loglardaki istemci adı (ör. {@code order-service})
	 * @param keySha256 ham anahtarın UTF-8 baytlarının SHA-256 özeti, hex
	 */
	public record Client(String name, String keySha256) {

		/** Özet loglara/aktüatöre düşmesin. */
		@Override
		public String toString() {
			boolean set = this.keySha256 != null && !this.keySha256.isBlank();
			return "Client[name=" + this.name + ", keySha256=" + (set ? "***" : "") + "]";
		}

	}

}
