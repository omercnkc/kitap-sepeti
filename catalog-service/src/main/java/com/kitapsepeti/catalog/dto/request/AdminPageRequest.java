package com.kitapsepeti.catalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Admin liste uçlarının sayfa parametreleri (query). Wrapper tipler: eksik parametre constructor binding'de
 * null gelir, varsayılan burada verilir.
 */
public record AdminPageRequest(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {

	public static final int DEFAULT_SIZE = 20;

	public AdminPageRequest {
		page = (page != null) ? page : 0;
		size = (size != null) ? size : DEFAULT_SIZE;
	}

}
