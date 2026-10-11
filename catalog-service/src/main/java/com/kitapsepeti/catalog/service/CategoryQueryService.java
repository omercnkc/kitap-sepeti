package com.kitapsepeti.catalog.service;

import java.util.Comparator;
import java.util.List;

import com.kitapsepeti.catalog.dto.response.CategoryRef;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.mapper.CategoryMapper;
import com.kitapsepeti.catalog.mapper.NameOrder;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kategori okuma: düz liste, Türkçe ada göre sıralı. */
@Service
@Transactional(readOnly = true)
public class CategoryQueryService {

	private final CategoryRepository categoryRepository;

	public CategoryQueryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	public List<CategoryRef> list() {
		return this.categoryRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(Category::getName, NameOrder.TURKISH).thenComparing(Category::getSlug))
			.map(CategoryMapper::toRef)
			.toList();
	}

}
