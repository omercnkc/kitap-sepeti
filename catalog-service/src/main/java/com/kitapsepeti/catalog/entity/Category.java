package com.kitapsepeti.catalog.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
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
 * Kategori ({@code categories} tablosu); {@code parent} ile ağaç oluşturur.
 * Alt kategoriler koleksiyon olarak tutulmaz; alt kategorisi olan kategori silinemez (DB RESTRICT).
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Üst kategori; null ise kök kategori. */
	@Setter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Category parent;

	@Setter
	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Setter
	@Column(name = "slug", nullable = false, length = 120)
	private String slug;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Kök kategori için {@code parent} null verilir. */
	public Category(Category parent, String name, String slug) {
		this.parent = parent;
		this.name = name;
		this.slug = slug;
	}

}
