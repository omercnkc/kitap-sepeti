package com.kitapsepeti.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.user.TestcontainersConfiguration;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.repository.UserRepository;
import com.kitapsepeti.user.service.OutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {

	private static final String PASSWORD = "Gizli-Parola-7391";

	private static final String OVERLONG_PASSWORD = "ş".repeat(40);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OutboxService outboxService;

	/** Test boyunca kullanılan ham parola ve token'lar; hiçbiri loglarda geçmemeli. */
	private final List<String> secrets = new ArrayList<>(List.of(PASSWORD, OVERLONG_PASSWORD));

	private record Tokens(String accessToken, String refreshToken) {
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.update("DELETE FROM refresh_tokens");
		jdbc.update("DELETE FROM outbox");
		jdbc.update("DELETE FROM addresses");
		jdbc.update("DELETE FROM users");
	}

	@AfterEach
	void secretsNeverLogged(CapturedOutput output) {
		for (String secret : secrets) {
			assertThat(output.getAll()).doesNotContain(secret);
		}
	}

	@Test
	void registerCreatesUserOutboxEventAndHashedRefreshToken() throws Exception {
		ResultActions result = perform("/api/auth/register", registerJson("Ayse.Yilmaz@Example.COM", PASSWORD))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900));
		Tokens tokens = tokens(result);

		Map<String, Object> user = jdbc.queryForMap("SELECT BIN_TO_UUID(id) AS id, email, password_hash FROM users");
		String userId = (String) user.get("id");
		assertThat(user.get("email")).isEqualTo("ayse.yilmaz@example.com");
		assertThat((String) user.get("password_hash")).startsWith("$2a$10$");

		List<Map<String, Object>> outbox = jdbc.queryForList(
				"SELECT aggregate_type, BIN_TO_UUID(aggregate_id) AS aggregate_id, event_type, payload, published_at FROM outbox");
		assertThat(outbox).hasSize(1);
		Map<String, Object> event = outbox.getFirst();
		assertThat(event.get("aggregate_type")).isEqualTo("user");
		assertThat(event.get("event_type")).isEqualTo("UserRegistered");
		assertThat(event.get("aggregate_id")).isEqualTo(userId);
		assertThat(event.get("published_at")).isNull();
		String payload = String.valueOf(event.get("payload"));
		assertThat((String) JsonPath.read(payload, "$.userId")).isEqualTo(userId);
		assertThat((Integer) JsonPath.read(payload, "$.eventVersion")).isEqualTo(1);
		assertThat((String) JsonPath.read(payload, "$.email")).isEqualTo("ayse.yilmaz@example.com");
		assertThat(payload.toLowerCase(Locale.ROOT)).doesNotContain("password").doesNotContain("$2a$");

		List<String> hashes = jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class);
		assertThat(hashes).hasSize(1);
		assertThat(hashes.getFirst()).hasSize(64)
			.isNotEqualTo(tokens.refreshToken())
			.isEqualTo(sha256(tokens.refreshToken()));
	}

	@Test
	void duplicateEmailWithDifferentCaseIsRejected() throws Exception {
		register("ali@kitapsepeti.com");

		perform("/api/auth/register", registerJson("ALI@KitapSepeti.com", PASSWORD))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

		assertThat(count("users")).isEqualTo(1);
		assertThat(count("outbox")).isEqualTo(1);
	}

	@Test
	void emailIsNormalizedWithRootLocaleEvenUnderTurkishDefault() throws Exception {
		Locale original = Locale.getDefault();
		Locale.setDefault(Locale.of("tr", "TR"));
		try {
			register("ALI@X.COM");
		}
		finally {
			Locale.setDefault(original);
		}

		assertThat(jdbc.queryForObject("SELECT email FROM users", String.class)).isEqualTo("ali@x.com");
	}

	@Test
	void passwordLongerThan72BytesIsValidationErrorNot500() throws Exception {
		perform("/api/auth/register", registerJson("uzun@kitapsepeti.com", OVERLONG_PASSWORD))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", hasItem("password")));

		assertThat(count("users")).isZero();
	}

	@Test
	void loginReturnsAccessTokenWithUserIdAndRole() throws Exception {
		register("giris@kitapsepeti.com");
		String userId = userIdOf("giris@kitapsepeti.com");

		Tokens tokens = tokens(perform("/api/auth/login", loginJson("  GIRIS@kitapsepeti.com ", PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900)));

		Jwt jwt = jwtDecoder.decode(tokens.accessToken());
		assertThat(jwt.getSubject()).isEqualTo(userId);
		assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
	}

	@Test
	void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
		register("var@kitapsepeti.com");

		String wrongPassword = perform("/api/auth/login", loginJson("var@kitapsepeti.com", "Yanlis-Parola-1"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andReturn().getResponse().getContentAsString();
		String unknownEmail = perform("/api/auth/login", loginJson("yok@kitapsepeti.com", PASSWORD))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andReturn().getResponse().getContentAsString();

		assertThat((String) JsonPath.read(wrongPassword, "$.detail"))
			.isEqualTo(JsonPath.read(unknownEmail, "$.detail"));
		assertThat(wrongPassword).isEqualTo(unknownEmail);
	}

	@Test
	void loginWithPasswordLongerThan72BytesIsInvalidCredentialsNot500() throws Exception {
		register("uzun-giris@kitapsepeti.com");

		perform("/api/auth/login", loginJson("uzun-giris@kitapsepeti.com", OVERLONG_PASSWORD))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void suspendedUserWithCorrectPasswordGets403() throws Exception {
		register("askida@kitapsepeti.com");
		jdbc.update("UPDATE users SET status = 'suspended' WHERE email = ?", "askida@kitapsepeti.com");

		perform("/api/auth/login", loginJson("askida@kitapsepeti.com", PASSWORD))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
	}

	@Test
	void refreshRotatesTokensAndRevokesOldOne() throws Exception {
		Tokens first = register("yenile@kitapsepeti.com");

		Tokens second = refresh(first.refreshToken());

		assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
		assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
		assertThat(revokedAt(first.refreshToken())).isNotNull();
		assertThat(revokedAt(second.refreshToken())).isNull();
	}

	@Test
	void reusedRefreshTokenRevokesWholeFamily(CapturedOutput output) throws Exception {
		Tokens first = register("tekrar@kitapsepeti.com");
		String userId = userIdOf("tekrar@kitapsepeti.com");
		Tokens second = refresh(first.refreshToken());

		perform("/api/auth/refresh", refreshJson(first.refreshToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		// Toplu iptal commit edilmiş olmalı: meşru görünen yeni token da artık geçersiz.
		perform("/api/auth/refresh", refreshJson(second.refreshToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class))
			.isZero();
		assertThat(output.getAll()).contains("refresh token reuse detected userId=" + userId);
	}

	@Test
	void expiredRefreshTokenIsRejected() throws Exception {
		Tokens tokens = register("suresi@kitapsepeti.com");
		jdbc.update("UPDATE refresh_tokens SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY");

		perform("/api/auth/refresh", refreshJson(tokens.refreshToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void concurrentDuplicateInsertIsRecognizedByConstraintName() {
		userRepository.saveAndFlush(new User("yaris@kitapsepeti.com", "hash", "A", "B"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(new User("yaris@kitapsepeti.com", "hash", "A", "B")))
			.isInstanceOfSatisfying(DataIntegrityViolationException.class,
					ex -> assertThat(DbConstraints.isViolated(ex, "uk_users_email")).isTrue());
	}

	@Test
	void outboxAppendRequiresSurroundingTransaction() {
		assertThatThrownBy(() -> outboxService.append("user", UUID.randomUUID(), "Test", Map.of()))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	/**
	 * Her yanıt: parola alanı/hash'i yok. Doğrulama hatasındaki {@code "field":"password"} alan adıdır,
	 * {@code "password":} anahtarı değildir; bu yüzden anahtar biçimi aranır.
	 */
	private ResultActions perform(String path, String json) throws Exception {
		ResultActions result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
		String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(body).doesNotContainPattern("\"password\"\\s*:")
			.doesNotContain("passwordHash")
			.doesNotContain("password_hash")
			.doesNotContain("$2a$");
		return result;
	}

	/** Token yanıtı: değerleri gizli listesine ekler ve önbelleğe alınmadığını doğrular. */
	private Tokens tokens(ResultActions result) throws Exception {
		var response = result.andReturn().getResponse();
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
		String body = response.getContentAsString(StandardCharsets.UTF_8);
		assertThat(body).doesNotContain("password");
		Tokens tokens = new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
		secrets.add(tokens.accessToken());
		secrets.add(tokens.refreshToken());
		return tokens;
	}

	private Tokens register(String email) throws Exception {
		return tokens(perform("/api/auth/register", registerJson(email, PASSWORD)).andExpect(status().isCreated()));
	}

	private Tokens refresh(String refreshToken) throws Exception {
		return tokens(perform("/api/auth/refresh", refreshJson(refreshToken)).andExpect(status().isOk()));
	}

	private String userIdOf(String email) {
		return jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM users WHERE email = ?", String.class, email);
	}

	private Object revokedAt(String rawRefreshToken) {
		return jdbc.queryForObject("SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?", Object.class,
				sha256(rawRefreshToken));
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private static String registerJson(String email, String password) {
		return """
				{"email":"%s","password":"%s","firstName":"Ali","lastName":"Veli","phone":"5551112233"}
				""".formatted(email, password);
	}

	private static String loginJson(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}

	private static String refreshJson(String refreshToken) {
		return """
				{"refreshToken":"%s"}
				""".formatted(refreshToken);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
