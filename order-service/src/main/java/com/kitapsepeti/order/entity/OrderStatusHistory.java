package com.kitapsepeti.order.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * Durum geçmişi satırı ({@code order_status_history}). Yalnızca {@link Order}'ın durum geçişleri yazar (stok geçişleri
 * yazmaz); ilk satır {@code null → pending}. Değişmez (Hibernate UPDATE yazmaz).
 */
@Entity
@Immutable
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	private Order order;

	/** Yalnızca ilk satırda null ({@code ck_order_status_history_initial}). */
	@Convert(converter = OrderStatusConverter.class)
	@Column(name = "from_status", updatable = false, length = 16)
	private OrderStatus fromStatus;

	@Convert(converter = OrderStatusConverter.class)
	@Column(name = "to_status", nullable = false, updatable = false, length = 16)
	private OrderStatus toStatus;

	/** Makine kodu ({@link OrderReasons}); başarısız siparişte failure code'un kendisi. */
	@Column(name = "reason", updatable = false, length = 64)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	OrderStatusHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, String reason, Instant createdAt) {
		this.order = order;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason;
		this.createdAt = createdAt;
	}

}
