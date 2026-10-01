package com.kitapsepeti.catalog.dto.request;

import java.util.UUID;

import com.kitapsepeti.catalog.validation.Slug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ad kırpılarak doğrulanır. Slug verilmezse addan üretilir; {@code parentId} null ise kök kategori. */
public record CreateCategoryRequest(@NotBlank @Size(max = 120) String name, @Slug(max = 120) String slug,
		UUID parentId) {

	public CreateCategoryRequest {
		name = (name != null) ? name.strip() : null;
	}

}
