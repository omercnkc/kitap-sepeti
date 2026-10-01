package com.kitapsepeti.catalog.mapper;

import java.util.List;

import com.kitapsepeti.catalog.dto.response.CategoryTreeResponse;
import com.kitapsepeti.catalog.entity.Category;

/** {@link Category} → ağaç düğümü; alt düğümler çağıran tarafından (sıralı) verilir. */
public final class CategoryMapper {

	private CategoryMapper() {
	}

	public static CategoryTreeResponse toTreeNode(Category category, List<CategoryTreeResponse> children) {
		return new CategoryTreeResponse(category.getId(), category.getName(), category.getSlug(), children);
	}

}
