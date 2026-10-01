package com.kitapsepeti.catalog.dto.request;

import com.kitapsepeti.catalog.validation.NullOrNotBlank;
import com.kitapsepeti.catalog.validation.Slug;
import jakarta.validation.constraints.Size;

/**
 * Kısmi güncelleme: null alan değiştirilmez; yalnızca ad değişirse slug korunur.
 * Üst kategori burada değişmez ({@link MoveCategoryRequest}).
 */
public record UpdateCategoryRequest(@NullOrNotBlank @Size(max = 120) String name, @Slug(max = 120) String slug) {

	public UpdateCategoryRequest {
		name = (name != null) ? name.strip() : null;
	}

}
