package com.kitapsepeti.payment.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox kaydı ({@code outbox} tablosu; catalog-service'teki sınıfın kopyası).
 * Diğer servislere gidecek olay, iş verisiyle aynı transaction'da buraya yazılır;
 * ayrı bir yayıncı yayınlanmamış kayıtları okuyup gönderir ve {@code publishedAt}'i doldurur.
 * <p>
 * Catalog'dan tek fark: id INSERT'ten önce uygulamada üretilir (aynı UUIDv7 üreticisi), çünkü payload'daki
 * {@code eventId} bu id'dir ve satır yazılırken bilinmesi gerekir.
 */
@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Olayın ait olduğu varlık türü, küçük harf: ör. "payment". */
	@Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
	private String aggregateType;

	/** O varlığın id'si, ör. ödeme id'si. */
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	/** Olay adı, PascalCase: ör. "PaymentSucceeded". */
	@Column(name = "event_type", nullable = false, updatable = false, length = 64)
	private String eventType;

	/** Olay içeriği; DB'de JSON kolonunda, Java'da ham JSON metni olarak tutulur. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, updatable = false)
	private String payload;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Null ise henüz yayınlanmadı; yayıncı gönderdikten sonra doldurur. */
	@Setter
	@Column(name = "published_at")
	private Instant publishedAt;

	/** Henüz yayınlanmamış yeni bir olay kaydı oluşturur. */
	public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload) {
		this.id = id;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
	}

}
