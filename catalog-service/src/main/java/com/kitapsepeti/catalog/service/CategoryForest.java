package com.kitapsepeti.catalog.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Category;
import com.kitapsepeti.catalog.mapper.NameOrder;

/**
 * Tek sorguda okunan tüm kategorilerden bellekte kurulan ağaç (kategori tablosu küçük).
 * Her seviye ada göre sıralı. {@code parent} LAZY olsa da yalnızca id'si okunur; ek sorgu çıkmaz.
 */
final class CategoryForest {

	private static final Comparator<Category> BY_NAME = Comparator.comparing(Category::getName, NameOrder.TURKISH)
		.thenComparing(Category::getSlug);

	private final Map<UUID, Category> byId = new HashMap<>();

	private final Map<UUID, List<Category>> childrenByParentId = new HashMap<>();

	private final List<Category> roots = new ArrayList<>();

	private CategoryForest(List<Category> categories) {
		for (Category category : categories) {
			this.byId.put(category.getId(), category);
		}
		for (Category category : categories) {
			UUID parentId = (category.getParent() != null) ? category.getParent().getId() : null;
			if (parentId == null || !this.byId.containsKey(parentId)) {
				this.roots.add(category);
			}
			else {
				this.childrenByParentId.computeIfAbsent(parentId, id -> new ArrayList<>()).add(category);
			}
		}
		this.roots.sort(BY_NAME);
		this.childrenByParentId.values().forEach(children -> children.sort(BY_NAME));
	}

	static CategoryForest of(List<Category> categories) {
		return new CategoryForest(categories);
	}

	List<Category> roots() {
		return this.roots;
	}

	List<Category> childrenOf(Category category) {
		return this.childrenByParentId.getOrDefault(category.getId(), List.of());
	}

	/** Kategori ve tüm alt kategorilerinin id'leri; kategori yoksa boş küme. */
	Set<UUID> subtreeIds(UUID categoryId) {
		Set<UUID> ids = new LinkedHashSet<>();
		Category start = this.byId.get(categoryId);
		if (start == null) {
			return ids;
		}
		Deque<Category> pending = new ArrayDeque<>();
		pending.push(start);
		while (!pending.isEmpty()) {
			Category current = pending.pop();
			if (ids.add(current.getId())) {
				childrenOf(current).forEach(pending::push);
			}
		}
		return ids;
	}

}
