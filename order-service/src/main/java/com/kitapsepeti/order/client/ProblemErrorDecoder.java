package com.kitapsepeti.order.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import feign.Response;
import feign.codec.ErrorDecoder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 2xx olmayan her yanıtı {@link RemoteProblemException}'a çevirir: durum + ProblemDetail {@code code} (+ Catalog stok
 * hatalarında {@code bookIds}). Gövde en fazla {@value #MAX_BODY_BYTES} bayt okunur, LOGLANMAZ ve exception'a konmaz;
 * okunamayan/ProblemDetail olmayan gövdede kod null. Hangi durumun ne anlama geldiğine gateway karar verir (aynı kod
 * işleme göre farklı sonuçtur). Retry-After'lı 503 de {@code RetryableException} olmaz (otomatik tekrar yok).
 */
class ProblemErrorDecoder implements ErrorDecoder {

	static final int MAX_BODY_BYTES = 16 * 1024;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public Exception decode(String methodKey, Response response) {
		JsonNode problem = read(response);
		String code = null;
		List<UUID> bookIds = new ArrayList<>();
		if (problem != null && problem.isObject()) {
			JsonNode codeNode = problem.get("code");
			if (codeNode != null && codeNode.isString()) {
				code = codeNode.stringValue();
			}
			JsonNode ids = problem.get("bookIds");
			if (ids != null && ids.isArray()) {
				for (JsonNode id : ids) {
					parseUuid(id).ifPresent(bookIds::add);
				}
			}
		}
		return new RemoteProblemException(response.status(), code, bookIds);
	}

	private static JsonNode read(Response response) {
		if (response.body() == null) {
			return null;
		}
		try (InputStream in = response.body().asInputStream()) {
			byte[] body = in.readNBytes(MAX_BODY_BYTES);
			return (body.length == 0) ? null : JSON.readTree(body);
		}
		catch (IOException | JacksonException ex) {
			return null;
		}
	}

	private static Optional<UUID> parseUuid(JsonNode node) {
		if (!node.isString()) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(node.stringValue()));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
