package com.kitapsepeti.catalog.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.dto.response.CategoryTreeResponse;
import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.mapper.CategoryMapper;
import com.kitapsepeti.catalog.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kategori okuma: tüm kategoriler tek sorguda okunur, ağaç bellekte kurulur. */
@Service
@Transactional(readOnly = true)
public class CategoryQueryService {

	private final CategoryRepository categoryRepository;

	public CategoryQueryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	/** Kök kategoriler ve alt ağaçları; her seviye ada göre sıralı. */
	public List<CategoryTreeResponse> tree() {
		CategoryForest forest = CategoryForest.of(this.categoryRepository.findAll());
		return forest.roots().stream().map(root -> toNode(forest, root)).toList();
	}

	/** Kategori ve tüm alt kategorilerinin id'leri; kategori yoksa boş küme. */
	public Set<UUID> subtreeIds(UUID categoryId) {
		return CategoryForest.of(this.categoryRepository.findAll()).subtreeIds(categoryId);
	}

	private static CategoryTreeResponse toNode(CategoryForest forest, Category category) {
		List<CategoryTreeResponse> children = forest.childrenOf(category).stream()
			.map(child -> toNode(forest, child))
			.toList();
		return CategoryMapper.toTreeNode(category, children);
	}

}
