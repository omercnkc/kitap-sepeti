package com.kitapsepeti.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * {@code GET /api/orders} query parametreleri; hepsi opsiyonel.
 * Catalog liste ucuyla ({@code BookSearchRequest}) aynı sayfalama kuralları:
 * {@code page >= 0} (varsayılan 0), {@code size 1..50} (varsayılan 20).
 * Sıralama sabit: {@code created_at DESC, id DESC}; sort parametresi yok.
 */
public record OrderListRequest(
		@Schema(defaultValue = "0") @Min(0) Integer page,
		@Schema(defaultValue = "20") @Min(1) @Max(50) Integer size) {

	public static final int DEFAULT_SIZE = 20;

	public OrderListRequest {
		page = (page != null) ? page : 0;
		size = (size != null) ? size : DEFAULT_SIZE;
	}

}
