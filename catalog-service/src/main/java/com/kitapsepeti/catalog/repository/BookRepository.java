package com.kitapsepeti.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * {@link Book} kayıtlarına erişim; filtreli listeleme {@link JpaSpecificationExecutor} ile yapılır.
 */
public interface BookRepository extends JpaRepository<Book, UUID>, JpaSpecificationExecutor<Book> {

	/**
	 * Kitabı yayınevi, yazarlar ve kategorilerle tek sorguda getirir; dönen nesnenin bu alanlarına
	 * transaction dışında da erişilebilir. İki koleksiyon da {@code Set} olduğu için birlikte fetch edilebilir.
	 */
	@EntityGraph(attributePaths = { "publisher", "authors", "categories" })
	Optional<Book> findWithDetailsById(UUID id);

	/**
	 * Sayfalı liste; yalnızca to-one yayınevi aynı sorguda gelir (sayım sorgusuna graph uygulanmaz).
	 * Koleksiyonlar burada fetch EDİLMEZ: sayfalamayla to-many fetch Hibernate'i bellekte sayfalamaya zorlar.
	 */
	@Override
	@EntityGraph(attributePaths = "publisher")
	Page<Book> findAll(Specification<Book> spec, Pageable pageable);

}
