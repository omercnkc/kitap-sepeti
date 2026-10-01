package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreateCategoryRequest;
import com.kitapsepeti.catalog.dto.request.MoveCategoryRequest;
import com.kitapsepeti.catalog.dto.request.UpdateCategoryRequest;
import com.kitapsepeti.catalog.dto.response.CategoryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.CategoryAdminService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kategori yönetimi (SecurityConfig: /api/admin/** yalnızca ADMIN). Liste düzdür; ağaç public GET /api/categories. */
@RestController
@RequestMapping(path = "/api/admin/categories", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminCategoryController {

	private static final String BASE_PATH = "/api/admin/categories/";

	private final CategoryAdminService categoryAdminService;

	public AdminCategoryController(CategoryAdminService categoryAdminService) {
		this.categoryAdminService = categoryAdminService;
	}

	@GetMapping
	public PageResponse<CategoryResponse> list(@Valid @ModelAttribute AdminPageRequest page) {
		return categoryAdminService.list(page);
	}

	@GetMapping("/{id}")
	public CategoryResponse get(@PathVariable UUID id) {
		return categoryAdminService.get(id);
	}

	@PostMapping
	public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
		CategoryResponse created = categoryAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
		return categoryAdminService.update(id, request);
	}

	/** Gövde {@code {"parentId": null}} kategoriyi kök yapar. */
	@PutMapping("/{id}/parent")
	public CategoryResponse move(@PathVariable UUID id, @RequestBody MoveCategoryRequest request) {
		return categoryAdminService.move(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		categoryAdminService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
