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
import org.hibernate.annotations.UuidGenerator;

/**
 * Oturum yenilemek için verilen refresh token kaydı ({@code refresh_tokens} tablosu).
 * Token değiştirilmez; iptal edilir ({@code revokedAt}) veya süresi dolar ({@code expiresAt}).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Token'ın sahibi; kullanıcı silinince token'ları da silinir (DB CASCADE). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	/** Token'ın kendisi değil hash'i saklanır; benzersiz. */
	@Column(name = "token_hash", nullable = false, updatable = false, length = 255)
	private String tokenHash;

	/** Bu andan sonra token geçersiz. */
	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	/** Doluysa token iptal edilmiş (ör. çıkış yapıldı); null ise aktif. */
	@Setter
	@Column(name = "revoked_at")
	private Instant revokedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Yeni, iptal edilmemiş bir token kaydı oluşturur. */
	public RefreshToken(User user, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

}
