package com.kitapsepeti.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Book;
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

}
