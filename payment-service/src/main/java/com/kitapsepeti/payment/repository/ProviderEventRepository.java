package com.kitapsepeti.payment.repository;

import java.util.UUID;

import com.kitapsepeti.payment.entity.ProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderEventRepository extends JpaRepository<ProviderEvent, UUID> {
}
