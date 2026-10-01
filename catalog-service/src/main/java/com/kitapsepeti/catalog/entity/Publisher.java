package com.kitapsepeti.catalog.entity;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Yayınevi ({@code publishers} tablosu). Kitabı olan yayınevi silinemez (DB RESTRICT).
 */
@Entity
@Table(name = "publishers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Publisher {

	/** Uygulama tarafında üretilen, zamana göre sıralı UUID v7; DB'de BINARY(16). */
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Setter
	@Column(name = "name", nullable = false, length = 160)
	private String name;

	/** URL'de kullanılan benzersiz kısa ad, ör. "can-yayinlari". */
	@Setter
	@Column(name = "slug", nullable = false, length = 160)
	private String slug;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Publisher(String name, String slug) {
		this.name = name;
		this.slug = slug;
	}

}
