package com.kitapsepeti.catalog.dto.request;

import com.kitapsepeti.catalog.validation.Slug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ad kırpılarak doğrulanır. Slug verilmezse addan üretilir. */
public record CreateCategoryRequest(@NotBlank @Size(max = 120) String name, @Slug(max = 120) String slug) {

	public CreateCategoryRequest {
		name = (name != null) ? name.strip() : null;
	}

}
