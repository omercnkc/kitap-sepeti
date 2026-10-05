package com.kitapsepeti.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String incomingRequestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
		String requestId = (incomingRequestId != null && !incomingRequestId.trim().isEmpty())
				? incomingRequestId.trim()
				: UUID.randomUUID().toString();

		ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
				.header(REQUEST_ID_HEADER, requestId)
				.build();

		ServerWebExchange mutatedExchange = exchange.mutate()
				.request(mutatedRequest)
				.build();

		mutatedExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
		mutatedExchange.getResponse().beforeCommit(() -> {
			mutatedExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
			return Mono.empty();
		});

		return chain.filter(mutatedExchange);
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
