package com.kitapsepeti.user.entity;

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
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox kaydı ({@code outbox} tablosu).
 * Diğer servislere gidecek olay, iş verisiyle aynı transaction'da buraya yazılır;
 * ayrı bir yayıncı yayınlanmamış kayıtları okuyup gönderir ve {@code publishedAt}'i doldurur.
 * Böylece "veri kaydedildi ama olay kayboldu" durumu yaşanmaz.
 */
@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Olayın ait olduğu varlık türü, ör. "User". */
	@Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
	private String aggregateType;

	/** O varlığın id'si, ör. kullanıcı id'si. */
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	/** Olay adı, ör. "UserRegistered". */
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
	public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
	}

}
