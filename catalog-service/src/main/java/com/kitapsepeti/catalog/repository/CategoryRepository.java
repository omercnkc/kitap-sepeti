package com.kitapsepeti.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link Category} kayıtlarına erişim.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

	Optional<Category> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/** Kök kategoriler (üst kategorisi olmayanlar). */
	List<Category> findAllByParentIsNull();

}
