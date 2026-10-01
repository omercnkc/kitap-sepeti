package com.kitapsepeti.catalog.entity;

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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Bir sipariş için ayrılan stok ({@code stock_reservations} tablosu).
 * Sipariş başına kitap başına tek kayıt ({@code uk_stock_reservations_order_book});
 * rezervasyonu olan kitap silinemez (DB RESTRICT).
 */
@Entity
@Table(name = "stock_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_id", nullable = false, updatable = false)
	private Book book;

	/** order-service'teki siparişin id'si; servisler arası olduğu için FK yok. */
	@Column(name = "order_id", nullable = false, updatable = false)
	private UUID orderId;

	/** Pozitif ({@code ck_stock_reservations_quantity}). */
	@Column(name = "quantity", nullable = false, updatable = false)
	private int quantity;

	/** DB'de küçük harf ('held'/'committed'/'released'); varsayılan Java'da da verilir. */
	@Setter
	@Convert(converter = ReservationStatusConverter.class)
	@Column(name = "status", nullable = false, length = 16)
	private ReservationStatus status = ReservationStatus.HELD;

	/** 'held' rezervasyon bu ana kadar onaylanmazsa serbest bırakılır. */
	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Yeni, 'held' durumunda rezervasyon. */
	public StockReservation(Book book, UUID orderId, int quantity, Instant expiresAt) {
		this.book = book;
		this.orderId = orderId;
		this.quantity = quantity;
		this.expiresAt = expiresAt;
	}

}
