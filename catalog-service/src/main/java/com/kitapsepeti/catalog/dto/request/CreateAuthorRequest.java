package com.kitapsepeti.catalog.dto.request;

import com.kitapsepeti.catalog.validation.Slug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ad kırpılarak doğrulanır. Slug verilmezse addan üretilir. */
public record CreateAuthorRequest(@NotBlank @Size(max = 160) String name, @Slug(max = 160) String slug) {

	public CreateAuthorRequest {
		name = (name != null) ? name.strip() : null;
	}

}
