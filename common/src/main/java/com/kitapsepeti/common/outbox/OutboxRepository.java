package com.kitapsepeti.common.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link OutboxEvent} kayıtlarına erişim. Servis repository taramasına bu paketi açıkça ekler
 * ({@code @AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)}).
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	/**
	 * En eski yayınlanmamış satırları kilitler; başka bir transaction'ın kilitlediği satırlar beklenmeden
	 * atlanır. {@code (published_at, created_at)} indeksi (InnoDB'de PK'yı da içerir) sıralamayı karşılar,
	 * böylece yalnızca dönen satırlar kilitlenir. Açık bir transaction içinde çağrılmalıdır.
	 */
	@Query(value = """
			SELECT * FROM outbox
			WHERE published_at IS NULL
			ORDER BY created_at, id
			LIMIT :batch
			FOR UPDATE SKIP LOCKED""", nativeQuery = true)
	List<OutboxEvent> lockUnpublishedBatch(@Param("batch") int batch);

}
