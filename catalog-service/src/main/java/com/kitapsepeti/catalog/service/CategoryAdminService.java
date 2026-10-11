package com.kitapsepeti.catalog.service;

import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreateCategoryRequest;
import com.kitapsepeti.catalog.dto.request.UpdateCategoryRequest;
import com.kitapsepeti.catalog.dto.response.CategoryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.exception.SlugAlreadyExistsException;
import com.kitapsepeti.catalog.mapper.CategoryMapper;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import com.kitapsepeti.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kategori yönetimi (düz liste). Silmede bağlı kitap ({@code fk_book_categories_category}) varsa 409
 * RESOURCE_IN_USE.
 */
@Service
@Transactional
public class CategoryAdminService {

	private static final int MAX_SLUG_LENGTH = 120;

	private final CategoryRepository categoryRepository;

	public CategoryAdminService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<CategoryResponse> list(AdminPageRequest page) {
		return PageResponse.of(this.categoryRepository.findAll(AdminPaging.byNameThenId(page)),
				CategoryMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public CategoryResponse get(UUID id) {
		return CategoryMapper.toResponse(require(id));
	}

	public CategoryResponse create(CreateCategoryRequest request) {
		String slug = Slugs.forCreate(request.slug(), request.name(), MAX_SLUG_LENGTH);
		if (this.categoryRepository.existsBySlug(slug)) {
			throw new SlugAlreadyExistsException();
		}
		Category category = new Category(request.name(), slug);
		return CategoryMapper.toResponse(this.categoryRepository.saveAndFlush(category));
	}

	public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
		Category category = require(id);
		if (request.name() != null) {
			category.setName(request.name());
		}
		if (request.slug() != null && !request.slug().equals(category.getSlug())) {
			if (this.categoryRepository.existsBySlug(request.slug())) {
				throw new SlugAlreadyExistsException();
			}
			category.setSlug(request.slug());
		}
		this.categoryRepository.flush();
		return CategoryMapper.toResponse(category);
	}

	public void delete(UUID id) {
		this.categoryRepository.delete(require(id));
		this.categoryRepository.flush();
	}

	private Category require(UUID id) {
		return this.categoryRepository.findById(id).orElseThrow(CategoryAdminService::notFound);
	}

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException("Category not found.");
	}

}
