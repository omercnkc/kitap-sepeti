package com.kitapsepeti.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link Category} kayıtlarına erişim.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

	Optional<Category> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/** Kök kategoriler (üst kategorisi olmayanlar). */
	List<Category> findAllByParentIsNull();

	/**
	 * Tüm kategoriler, satırlar transaction sonuna kadar kilitli ({@code SELECT ... FOR UPDATE}).
	 * Eşzamanlı iki taşıma birbirinin sonucunu görmeden döngü kuramasın diye taşımalar sıraya girer.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Category c")
	List<Category> findAllForUpdate();

}
