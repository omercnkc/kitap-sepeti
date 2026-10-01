package com.kitapsepeti.catalog.mapper;

import java.util.List;
import java.util.UUID;

import com.kitapsepeti.catalog.dto.response.CategoryResponse;
import com.kitapsepeti.catalog.dto.response.CategoryTreeResponse;
import com.kitapsepeti.catalog.entity.Category;

/** {@link Category} → ağaç düğümü (alt düğümler çağıran tarafından, sıralı verilir) veya admin düz yanıtı. */
public final class CategoryMapper {

	private CategoryMapper() {
	}

	public static CategoryTreeResponse toTreeNode(Category category, List<CategoryTreeResponse> children) {
		return new CategoryTreeResponse(category.getId(), category.getName(), category.getSlug(), children);
	}

	/** LAZY {@code parent} proxy'sinden yalnızca id okunur; ek sorgu çıkmaz. */
	public static CategoryResponse toResponse(Category category) {
		UUID parentId = (category.getParent() != null) ? category.getParent().getId() : null;
		return new CategoryResponse(category.getId(), parentId, category.getName(), category.getSlug(),
				category.getCreatedAt(), category.getUpdatedAt());
	}

}
