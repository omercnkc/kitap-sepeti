package com.kitapsepeti.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Admin yazar listesi query parametreleri. {@code q} opsiyonel (addan LIKE); sayfalama
 * {@link AdminPageRequest} ile aynı kurallar.
 */
public record AdminAuthorListRequest(
		@Schema(description = "Yazar adında arama (LIKE); boş veya yalnızca boşluk yok sayılır") @Size(min = 2,
				max = 100) String q,
		@Schema(defaultValue = "0") @Min(0) Integer page,
		@Schema(defaultValue = "20") @Min(1) @Max(100) Integer size) {

	public AdminAuthorListRequest {
		q = (q != null && !q.isBlank()) ? q.strip() : null;
		page = (page != null) ? page : 0;
		size = (size != null) ? size : AdminPageRequest.DEFAULT_SIZE;
	}

}
