package com.kitapsepeti.payment.repository;

import java.util.List;
import java.util.UUID;

import com.kitapsepeti.payment.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link OutboxEvent} kayıtlarına erişim.
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
