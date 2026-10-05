package com.kitapsepeti.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
public class InternalPathFilter implements WebFilter, Ordered {

	private static final Logger log = LoggerFactory.getLogger(InternalPathFilter.class);
	private final JsonMapper jsonMapper;

	public InternalPathFilter(Optional<JsonMapper> jsonMapper) {
		this.jsonMapper = jsonMapper.orElseGet(() -> JsonMapper.builder().build());
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();
		if (isInternalPath(path)) {
			log.warn("Blocked external request to internal path: {}", path);
			return onNotFound(exchange);
		}
		return chain.filter(exchange);
	}

	private boolean isInternalPath(String path) {
		if (path == null) {
			return false;
		}
		String normalized = path.toLowerCase();
		return normalized.equals("/internal")
				|| normalized.startsWith("/internal/")
				|| normalized.contains("/internal/")
				|| normalized.endsWith("/internal");
	}

	private Mono<Void> onNotFound(ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.NOT_FOUND);
		response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", "Bulunamadı");
		body.put("status", HttpStatus.NOT_FOUND.value());
		body.put("detail", "İstenen kaynak bulunamadı.");
		body.put("instance", exchange.getRequest().getPath().value());
		body.put("code", "NOT_FOUND");

		byte[] bytes;
		try {
			bytes = this.jsonMapper.writeValueAsBytes(body);
		} catch (Exception ex) {
			bytes = ("{\"status\":404,\"title\":\"Bulunamadı\",\"code\":\"NOT_FOUND\"}").getBytes(StandardCharsets.UTF_8);
		}

		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		return response.writeWith(Mono.just(buffer));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}

}
