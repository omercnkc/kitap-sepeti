package com.kitapsepeti.catalog.mapper;

import com.kitapsepeti.catalog.dto.response.CategoryRef;
import com.kitapsepeti.catalog.dto.response.CategoryResponse;
import com.kitapsepeti.catalog.entity.Category;

/** {@link Category} → public özet veya admin yanıtı. */
public final class CategoryMapper {

	private CategoryMapper() {
	}

	public static CategoryRef toRef(Category category) {
		return new CategoryRef(category.getId(), category.getName(), category.getSlug());
	}

	public static CategoryResponse toResponse(Category category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getCreatedAt(),
				category.getUpdatedAt());
	}

}
