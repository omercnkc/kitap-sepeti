package com.kitapsepeti.user.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.common.outbox.OutboxService;
import com.kitapsepeti.user.dto.request.LoginRequest;
import com.kitapsepeti.user.dto.request.RefreshRequest;
import com.kitapsepeti.user.dto.request.RegisterRequest;
import com.kitapsepeti.user.dto.response.TokenResponse;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.entity.UserStatus;
import com.kitapsepeti.user.exception.AccountSuspendedException;
import com.kitapsepeti.user.exception.EmailAlreadyExistsException;
import com.kitapsepeti.user.exception.InvalidCredentialsException;
import com.kitapsepeti.user.repository.UserRepository;
import com.kitapsepeti.user.security.JwtProperties;
import com.kitapsepeti.user.service.event.UserRegisteredEvent;
import com.kitapsepeti.user.validation.TrPhones;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kayıt, giriş ve token yenileme akışları. */
@Service
public class AuthService {

	static final String USER_AGGREGATE = "user";

	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

	/** BCrypt yalnızca ilk 72 byte'ı kullanır; Spring daha uzununu hata ile reddeder. */
	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final RefreshTokenService refreshTokenService;

	private final OutboxService outboxService;

	private final JwtProperties jwtProperties;

	private final Clock clock;

	/**
	 * Kayıtlı olmayan e-postada da bir bcrypt karşılaştırması yapılır; böylece yanıt süresinden
	 * e-postanın kayıtlı olup olmadığı anlaşılamaz.
	 */
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokenService, OutboxService outboxService, JwtProperties jwtProperties,
			Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.outboxService = outboxService;
		this.jwtProperties = jwtProperties;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public TokenResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyExistsException();
		}

		User user = new User(email, passwordEncoder.encode(request.password()), request.firstName(),
				request.lastName());
		user.setPhone(TrPhones.toCanonicalOrNull(request.phone()));
		try {
			user = userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// existsByEmail ile insert arasında aynı e-postayla eşzamanlı kayıt.
			if (DbConstraints.isViolated(ex, EMAIL_UNIQUE_CONSTRAINT)) {
				throw new EmailAlreadyExistsException();
			}
			throw ex;
		}

		outboxService.append(USER_AGGREGATE, user.getId(), UserRegisteredEvent.TYPE, new UserRegisteredEvent(
				UserRegisteredEvent.VERSION, user.getId(), user.getEmail(), user.getFirstName(), clock.instant()));
		return issueTokens(user, refreshTokenService.issue(user));
	}

	@Transactional
	public TokenResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		Optional<User> found = userRepository.findByEmail(email);
		if (found.isEmpty()) {
			passwordMatches(request.password(), dummyPasswordHash);
			throw new InvalidCredentialsException();
		}

		User user = found.get();
		if (!passwordMatches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new AccountSuspendedException();
		}
		return issueTokens(user, refreshTokenService.issue(user));
	}

	/**
	 * Transactional DEĞİL: transaction'ın sahibi {@link RefreshTokenService#rotate}. Burada açılsaydı
	 * InvalidRefreshTokenException dış transaction'ı geri alır, tekrar kullanımda yapılan toplu iptal kaybolurdu.
	 */
	public TokenResponse refresh(RefreshRequest request) {
		RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
		return issueTokens(rotation.user(), rotation.refreshToken());
	}

	/** Locale.ROOT zorunlu: Türkçe locale'de "I".toLowerCase() noktasız "ı" verir. */
	static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Girişte parola uzunluğu doğrulanmadığı için 72 byte'tan uzun parola bcrypt'e verilmez (hata
	 * fırlatırdı); yine de süre eşit kalsın diye dummy karşılaştırma yapılır ve eşleşmedi sayılır.
	 */
	private boolean passwordMatches(String rawPassword, String passwordHash) {
		if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
			passwordEncoder.matches("", dummyPasswordHash);
			return false;
		}
		return passwordEncoder.matches(rawPassword, passwordHash);
	}

	private TokenResponse issueTokens(User user, String refreshToken) {
		AccessToken accessToken = jwtService.issueAccessToken(user);
		return TokenResponse.bearer(accessToken.value(), refreshToken, jwtProperties.accessTtl().toSeconds());
	}

}
