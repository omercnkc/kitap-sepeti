package com.kitapsepeti.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Admin liste uçlarının sayfa parametreleri (query). Wrapper tipler: eksik parametre constructor binding'de
 * null gelir, varsayılan burada verilir.
 */
public record AdminPageRequest(
		@Schema(defaultValue = "0") @Min(0) Integer page,
		@Schema(defaultValue = "20") @Min(1) @Max(100) Integer size) {

	public static final int DEFAULT_SIZE = 20;

	public AdminPageRequest {
		page = (page != null) ? page : 0;
		size = (size != null) ? size : DEFAULT_SIZE;
	}

}
