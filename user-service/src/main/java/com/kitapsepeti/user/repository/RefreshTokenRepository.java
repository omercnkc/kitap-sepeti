package com.kitapsepeti.user.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link RefreshToken} kayıtlarına erişim.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/** İstemciden gelen token hash'lenip bu metotla aranır; ham token DB'de hiç tutulmaz. */
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Token'ı yalnızca hâlâ aktifse iptal eder; tek UPDATE olduğu için eşzamanlı iki istekten
	 * yalnızca biri 1 alır. 0 = token zaten iptal edilmişti (tekrar kullanım).
	 */
	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
	int revokeIfActive(@Param("id") UUID id, @Param("now") Instant now);

	/** Kullanıcının tüm aktif token'larını iptal eder (tekrar kullanım tespitinde). */
	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
	int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

}
