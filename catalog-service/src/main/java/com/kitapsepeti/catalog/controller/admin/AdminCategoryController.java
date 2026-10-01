package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.config.OpenApiConfig;
import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreateCategoryRequest;
import com.kitapsepeti.catalog.dto.request.MoveCategoryRequest;
import com.kitapsepeti.catalog.dto.request.UpdateCategoryRequest;
import com.kitapsepeti.catalog.dto.response.CategoryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.CategoryAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = OpenApiConfig.TAG_ADMIN_CATEGORIES, description = "Kategori yönetimi. Liste düzdür; ağaç için "
		+ "`GET /api/categories`.")
public class AdminCategoryController {

	private static final String BASE_PATH = "/api/admin/categories/";

	private final CategoryAdminService categoryAdminService;

	public AdminCategoryController(CategoryAdminService categoryAdminService) {
		this.categoryAdminService = categoryAdminService;
	}

	@GetMapping
	@Operation(operationId = "listCategories", summary = "Kategorileri listele (düz)", description = "Ada göre sıralı.")
	@ApiResponse(responseCode = "200", description = "Sayfa.")
	public PageResponse<CategoryResponse> list(@Valid @ParameterObject @ModelAttribute AdminPageRequest page) {
		return categoryAdminService.list(page);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getCategory", summary = "Kategori getir")
	@ApiResponse(responseCode = "200", description = "Kategori.")
	public CategoryResponse get(@Parameter(description = "Kategori id'si") @PathVariable UUID id) {
		return categoryAdminService.get(id);
	}

	@PostMapping
	@Operation(operationId = "createCategory", summary = "Kategori oluştur",
			description = "`parentId` yoksa kök kategori; olmayan `parentId` → 400 `VALIDATION_FAILED`. `slug` "
					+ "verilmezse addan üretilir.")
	@ApiResponse(responseCode = "201", description = "Oluşturuldu.", headers = @Header(name = "Location",
			description = "Yeni kategorinin yolu.", schema = @Schema(type = "string", format = "uri-reference")))
	@ApiResponse(responseCode = "409", description = "`SLUG_ALREADY_EXISTS`: slug kullanımda.")
	public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
		CategoryResponse created = categoryAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	@Operation(operationId = "updateCategory", summary = "Kategori güncelle",
			description = "Kısmi güncelleme; yalnızca ad değişirse slug korunur. Üst kategori burada değişmez.")
	@ApiResponse(responseCode = "200", description = "Güncellenmiş kategori.")
	@ApiResponse(responseCode = "409", description = "`SLUG_ALREADY_EXISTS`: slug kullanımda.")
	public CategoryResponse update(@Parameter(description = "Kategori id'si") @PathVariable UUID id,
			@Valid @RequestBody UpdateCategoryRequest request) {
		return categoryAdminService.update(id, request);
	}

	/** Gövde {@code {"parentId": null}} kategoriyi kök yapar. */
	@PutMapping("/{id}/parent")
	@Operation(operationId = "moveCategory", summary = "Kategoriyi taşı",
			description = "`{\"parentId\": null}` kategoriyi kök yapar; olmayan `parentId` → 400 `VALIDATION_FAILED`.")
	@ApiResponse(responseCode = "200", description = "Taşınmış kategori.")
	@ApiResponse(responseCode = "409", description = "`CATEGORY_CYCLE`: yeni üst kategori, kategorinin kendisi veya "
			+ "alt ağacında.")
	public CategoryResponse move(@Parameter(description = "Kategori id'si") @PathVariable UUID id,
			@RequestBody MoveCategoryRequest request) {
		return categoryAdminService.move(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(operationId = "deleteCategory", summary = "Kategori sil")
	@ApiResponse(responseCode = "204", description = "Silindi.")
	@ApiResponse(responseCode = "409", description = "`RESOURCE_IN_USE`: alt kategorisi veya bağlı kitabı var.")
	public ResponseEntity<Void> delete(@Parameter(description = "Kategori id'si") @PathVariable UUID id) {
		categoryAdminService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
