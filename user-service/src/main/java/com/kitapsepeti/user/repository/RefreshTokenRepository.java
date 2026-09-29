package com.kitapsepeti.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link RefreshToken} kayıtlarına erişim.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/** İstemciden gelen token hash'lenip bu metotla aranır; ham token DB'de hiç tutulmaz. */
	Optional<RefreshToken> findByTokenHash(String tokenHash);

}
