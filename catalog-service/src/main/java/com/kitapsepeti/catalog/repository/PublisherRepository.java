package com.kitapsepeti.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link Publisher} kayıtlarına erişim.
 */
public interface PublisherRepository extends JpaRepository<Publisher, UUID> {

	Optional<Publisher> findBySlug(String slug);

	boolean existsBySlug(String slug);

}
