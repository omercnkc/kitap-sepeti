package com.kitapsepeti.user.entity;

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
 * Kullanıcının teslimat adresi ({@code addresses} tablosu).
 * Kullanıcı başına en fazla bir varsayılan adres olabilir; bunu DB'deki
 * {@code default_owner} generated kolonu + UNIQUE indeks garanti eder
 * (o kolon entity'de bilerek yok, DB kendisi hesaplar).
 */
@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Adresin sahibi; sonradan başka kullanıcıya taşınamaz. Kullanıcı silinince adres de silinir (DB CASCADE). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	/** Kullanıcının verdiği kısa ad, ör. "Ev", "İş". */
	@Setter
	@Column(name = "label", length = 40)
	private String label;

	@Setter
	@Column(name = "recipient_name", nullable = false, length = 120)
	private String recipientName;

	@Setter
	@Column(name = "phone", nullable = false, length = 32)
	private String phone;

	@Setter
	@Column(name = "line1", nullable = false, length = 200)
	private String line1;

	@Setter
	@Column(name = "line2", length = 200)
	private String line2;

	@Setter
	@Column(name = "district", length = 80)
	private String district;

	@Setter
	@Column(name = "city", nullable = false, length = 80)
	private String city;

	@Setter
	@Column(name = "postal_code", length = 16)
	private String postalCode;

	/** ISO 3166-1 alpha-2 ülke kodu (CHAR(2)). */
	@Setter
	@Column(name = "country", nullable = false, length = 2)
	private String country = "TR";

	/** Varsayılan adres mi? Lombok erişimcileri: {@code isDefault()} / {@code setDefault(boolean)}. */
	@Setter
	@Column(name = "is_default", nullable = false)
	private boolean isDefault = false;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Zorunlu alanlarla yeni adres; country="TR", isDefault=false başlar. */
	public Address(User user, String recipientName, String phone, String line1, String city) {
		this.user = user;
		this.recipientName = recipientName;
		this.phone = phone;
		this.line1 = line1;
		this.city = city;
	}

}
