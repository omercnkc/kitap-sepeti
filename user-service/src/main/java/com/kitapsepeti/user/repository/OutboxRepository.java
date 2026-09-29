package com.kitapsepeti.user.repository;

import java.util.UUID;

import com.kitapsepeti.user.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link OutboxEvent} kayıtlarına erişim. Şimdilik sadece temel CRUD; yayıncı
 * eklendiğinde yayınlanmamış kayıtları getiren sorgu buraya gelecek.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

}
