package com.kitapsepeti.gateway.ratelimit;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * RequestRateLimiter {@code throw-on-limit=true} iken fırlatılan 429'u JwtAuth ile aynı
 * Problem Details gövdesine çevirir. Diğer hataları zincirdeki sonraki handler'a bırakır.
 */
@Component
@Order(-2)
public class RateLimitExceededExceptionHandler implements WebExceptionHandler {

	static final String CODE = "RATE_LIMIT_EXCEEDED";
	static final String TITLE = "İstek sınırı aşıldı";
	static final String DETAIL = "Bu uç için kısa sürede çok fazla istek gönderildi. Lütfen biraz sonra tekrar dene.";
	static final String RETRY_AFTER_HEADER = "Retry-After";

	private final JsonMapper jsonMapper;
	private final int retryAfterSeconds;

	public RateLimitExceededExceptionHandler(Optional<JsonMapper> jsonMapper,
			@Value("${app.rate-limit.retry-after-seconds:60}") int retryAfterSeconds) {
		this.jsonMapper = jsonMapper.orElseGet(() -> JsonMapper.builder().build());
		this.retryAfterSeconds = retryAfterSeconds;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		if (!isTooManyRequests(ex)) {
			return Mono.error(ex);
		}

		ServerHttpResponse response = exchange.getResponse();
		if (response.isCommitted()) {
			return Mono.error(ex);
		}

		response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		response.getHeaders().set(RETRY_AFTER_HEADER, String.valueOf(this.retryAfterSeconds));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", TITLE);
		body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
		body.put("detail", DETAIL);
		body.put("instance", exchange.getRequest().getPath().value());
		body.put("code", CODE);

		byte[] bytes;
		try {
			bytes = this.jsonMapper.writeValueAsBytes(body);
		}
		catch (Exception writeEx) {
			bytes = ("{\"type\":\"about:blank\",\"title\":\"" + TITLE + "\",\"status\":429,\"detail\":\""
					+ DETAIL + "\",\"instance\":\"" + exchange.getRequest().getPath().value()
					+ "\",\"code\":\"" + CODE + "\"}").getBytes(StandardCharsets.UTF_8);
		}

		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		return response.writeWith(Mono.just(buffer));
	}

	private static boolean isTooManyRequests(Throwable ex) {
		if (ex instanceof HttpClientErrorException clientError) {
			return is429(clientError.getStatusCode());
		}
		if (ex instanceof ResponseStatusException statusError) {
			return is429(statusError.getStatusCode());
		}
		return false;
	}

	private static boolean is429(HttpStatusCode status) {
		return status != null && status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
	}

}
