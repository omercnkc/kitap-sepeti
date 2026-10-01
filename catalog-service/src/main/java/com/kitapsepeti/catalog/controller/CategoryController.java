package com.kitapsepeti.catalog.controller;

import java.util.List;

import com.kitapsepeti.catalog.dto.response.CategoryTreeResponse;
import com.kitapsepeti.catalog.service.CategoryQueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Herkese açık kategori ağacı (SecurityConfig: GET /api/categories/** permitAll). */
@RestController
@RequestMapping(path = "/api/categories", produces = MediaType.APPLICATION_JSON_VALUE)
public class CategoryController {

	private final CategoryQueryService categoryQueryService;

	public CategoryController(CategoryQueryService categoryQueryService) {
		this.categoryQueryService = categoryQueryService;
	}

	@GetMapping
	public List<CategoryTreeResponse> tree() {
		return categoryQueryService.tree();
	}

}
