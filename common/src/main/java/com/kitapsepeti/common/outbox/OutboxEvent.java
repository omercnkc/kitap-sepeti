package com.kitapsepeti.common.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox kaydı ({@code outbox} tablosu; DDL user, catalog ve payment'ta birebir aynı).
 * Diğer servislere gidecek olay, iş verisiyle aynı transaction'da buraya yazılır;
 * {@link OutboxRelay} yayınlanmamış kayıtları okuyup gönderir ve {@code publishedAt}'i doldurur.
 * Böylece "veri kaydedildi ama olay kayboldu" durumu yaşanmaz.
 * <p>
 * id INSERT'ten önce uygulamada üretilir ({@code @UuidGenerator(VERSION_7)} ile aynı UUIDv7 üreticisi); payload
 * olay id'sini içerecekse ({@link OutboxService#append(String, UUID, String, java.util.function.Function)})
 * satır yazılırken bilinmesi gerekir. Servis entity taramasına bu paketi açıkça ekler.
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Olayın ait olduğu varlık türü, küçük harf: ör. "book". */
	@Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
	private String aggregateType;

	/** O varlığın id'si, ör. kitap id'si. */
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	/** Olay adı, PascalCase: ör. "BookUpserted". */
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
	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEvent() {
	}

	/** Henüz yayınlanmamış yeni bir olay kaydı; id burada üretilir. */
	public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
		this(newId(), aggregateType, aggregateId, eventType, payload);
	}

	/** Henüz yayınlanmamış yeni bir olay kaydı; id önceden üretilmiş ({@link #newId()}). */
	public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload) {
		this.id = id;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
	}

	/** Yeni olay id'si (UUIDv7, zaman sıralı). */
	public static UUID newId() {
		return UuidVersion7Strategy.INSTANCE.generateUuid(null);
	}

	public UUID getId() {
		return this.id;
	}

	public String getAggregateType() {
		return this.aggregateType;
	}

	public UUID getAggregateId() {
		return this.aggregateId;
	}

	public String getEventType() {
		return this.eventType;
	}

	public String getPayload() {
		return this.payload;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

}
