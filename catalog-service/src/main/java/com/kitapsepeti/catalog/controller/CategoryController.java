package com.kitapsepeti.catalog.controller;

import java.util.List;

import com.kitapsepeti.catalog.config.OpenApiConfig;
import com.kitapsepeti.catalog.dto.response.CategoryRef;
import com.kitapsepeti.catalog.service.CategoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Herkese açık kategori listesi (SecurityConfig: GET /api/categories/** permitAll). */
@RestController
@RequestMapping(path = "/api/categories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_CATEGORIES, description = "Kategori listesi. Kimlik gerekmez.")
public class CategoryController {

	private final CategoryQueryService categoryQueryService;

	public CategoryController(CategoryQueryService categoryQueryService) {
		this.categoryQueryService = categoryQueryService;
	}

	@GetMapping
	@Operation(operationId = "listPublicCategories", summary = "Kategorileri listele",
			description = "Düz liste; Türkçe ada göre sıralı.")
	@ApiResponse(responseCode = "200", description = "Kategoriler (boş olabilir).")
	public List<CategoryRef> list() {
		return categoryQueryService.list();
	}

}
