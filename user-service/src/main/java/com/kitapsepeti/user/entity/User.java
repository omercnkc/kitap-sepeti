package com.kitapsepeti.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Sisteme kayıtlı kullanıcı ({@code users} tablosu).
 * Adresler ve refresh token'lar kendi tablolarında {@code user_id} ile bu kayda bağlanır.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	/** Uygulama tarafında üretilen, zamana göre sıralı UUID v7; DB'de BINARY(16). */
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Benzersiz; büyük/küçük harf farkı gözetilmez (DB collation'ı sayesinde). */
	@Setter
	@Column(name = "email", nullable = false, length = 255)
	private String email;

	/** Şifrenin kendisi değil, hash'i saklanır. */
	@Setter
	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Setter
	@Column(name = "first_name", nullable = false, length = 80)
	private String firstName;

	@Setter
	@Column(name = "last_name", nullable = false, length = 80)
	private String lastName;

	@Setter
	@Column(name = "phone", length = 32)
	private String phone;

	/** DB'de küçük harf ('active'/'suspended'); varsayılan Java'da da verilir, DB default'una güvenilmez. */
	@Setter
	@Convert(converter = UserStatusConverter.class)
	@Column(name = "status", nullable = false, length = 16)
	private UserStatus status = UserStatus.ACTIVE;

	/** DB'de enum adıyla ('USER'/'ADMIN'). */
	@Setter
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 16)
	private Role role = Role.USER;

	/** İlk kayıtta Hibernate doldurur, sonra değişmez. */
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Her güncellemede Hibernate yeniler. */
	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Zorunlu alanlarla yeni kullanıcı; status=ACTIVE, role=USER başlar. */
	public User(String email, String passwordHash, String firstName, String lastName) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.firstName = firstName;
		this.lastName = lastName;
	}

}
