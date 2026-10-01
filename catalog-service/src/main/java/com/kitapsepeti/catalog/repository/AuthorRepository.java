package com.kitapsepeti.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link Author} kayıtlarına erişim.
 */
public interface AuthorRepository extends JpaRepository<Author, UUID> {

	Optional<Author> findBySlug(String slug);

	boolean existsBySlug(String slug);

}
