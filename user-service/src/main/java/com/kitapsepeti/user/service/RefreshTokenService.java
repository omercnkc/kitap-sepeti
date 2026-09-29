package com.kitapsepeti.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.kitapsepeti.user.entity.RefreshToken;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.entity.UserStatus;
import com.kitapsepeti.user.exception.InvalidRefreshTokenException;
import com.kitapsepeti.user.repository.RefreshTokenRepository;
import com.kitapsepeti.user.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opak refresh token üretimi ve rotasyonu.
 * Ham token 32 rastgele byte'tır (Base64URL); DB'de yalnızca SHA-256 hex'i tutulur. Yüksek entropili
 * rastgele değerde yavaş hash (bcrypt) gerekmez ve bcrypt tuzlu olduğu için hash ile arama yapılamazdı.
 * Her kullanımda token iptal edilip yenisi verilir; iptal edilmiş bir token tekrar gelirse token
 * çalınmış sayılır ve kullanıcının tüm aktif token'ları iptal edilir.
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private static final int TOKEN_BYTES = 32;

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final SecureRandom secureRandom = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;

	private final JwtProperties properties;

	private final Clock clock;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties properties, Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/** Yeni refresh token kaydeder ve ham değeri döndürür. */
	@Transactional
	public String issue(User user) {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		String rawToken = ENCODER.encodeToString(bytes);
		Instant expiresAt = clock.instant().plus(properties.refreshTtl());
		refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), expiresAt));
		return rawToken;
	}

	/**
	 * Token'ı tüketip yenisini verir. {@code noRollbackFor}: tekrar kullanımda yapılan toplu iptal,
	 * exception fırlatılsa da commit edilmelidir; aksi halde çalınan token ailesi aktif kalırdı.
	 */
	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public RotationResult rotate(String rawToken) {
		Instant now = clock.instant();
		RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
			.orElseThrow(InvalidRefreshTokenException::new);
		if (!token.getExpiresAt().isAfter(now)) {
			throw new InvalidRefreshTokenException();
		}

		UUID userId = token.getUser().getId();
		if (refreshTokenRepository.revokeIfActive(token.getId(), now) == 0) {
			refreshTokenRepository.revokeAllActiveByUserId(userId, now);
			log.warn("refresh token reuse detected userId={}", userId);
			throw new InvalidRefreshTokenException();
		}

		User user = token.getUser();
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new InvalidRefreshTokenException();
		}
		return new RotationResult(user, issue(user));
	}

	/** SHA-256, 64 karakter küçük harf hex. */
	static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

}
