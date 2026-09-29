# Active Context

## Son değişiklikler
- Kök parent/aggregator `pom.xml` oluşturuldu; `user-service/pom.xml` bu parent'tan
  miras alacak şekilde sadeleştirildi. `.\mvnw.cmd clean package -DskipTests` başarılı.
- Kök `.gitignore` eklendi (`.env` ignore ediliyor).
- Kökte Docker Compose ile MySQL 8.4 kuruldu (`user_db` + `user_svc`); healthcheck, yetki izolasyonu
  ve volume kalıcılığı doğrulandı.
- Açık konu: yeni servis için şema/kullanıcı eklemek `MYSQL_DATABASE`/`MYSQL_USER` ile yapılamaz
  (bunlar tek şema destekler ve sadece boş volume'da çalışır); ikinci servis geldiğinde yöntem kararlaştırılacak.

- user-service `application.yml` ile Docker'daki `user_db`'ye bağlandı; Hikari, Flyway ve Tomcat (8081)
  doğrulandı, `/` isteği 401 döndü. `application.yaml` silindi.

- Spring Modulith tamamen kaldırıldı. Flyway V1 (`users`, `addresses`, `refresh_tokens`, `outbox`)
  `user_db`'ye uygulandı; UNIQUE/CHECK/FK kısıtları elle test edildi, tablolar boş bırakıldı.

- Entity (`entity/`: User, Address, RefreshToken, OutboxEvent, UserStatus+UserStatusConverter, Role) ve
  repository (`repository/`) katmanı eklendi; `validate` ek düzeltme gerektirmeden geçti.
- Testler Testcontainers MySQL 8.4 ile (`TestcontainersConfiguration`, `@ServiceConnection`); H2 yok.

- Entity, repository ve V1 SQL'e Türkçe açıklama yorumları eklendi. V1'in checksum'ı değişti
  (1293545622 → -625709255); yerel `user_db` Flyway repair ile hizalandı. V1'i eski haliyle
  uygulamış başka bir DB varsa orada da repair gerekir.
- Kural: uygulanmış migration dosyaları (yorum dahil) bir daha değiştirilmez; değişiklik = yeni V2, V3...

- SecurityConfig (BCrypt + stateless deny-by-default filter chain) eklendi; 11 test yeşil.

- RS256 JWT altyapısı tamamlandı (18 test yeşil):
  - Anahtarlar: kökte `secrets/jwt/{private,public}.pem` (git dışı), test-only çift
    `user-service/src/test/resources/jwt/test-{private,public}.pem` (izleniyor). `.gitignore`: `secrets/`,
    `*.pem`, `!user-service/src/test/resources/jwt/*.pem`.
  - `.env`: `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION` (file: mutlak yol); `.env.example` güncel.
  - Bağımlılık: `spring-boot-starter-security-oauth2-resource-server` (Boot 4 adı; eski
    `spring-boot-starter-oauth2-resource-server` deprecated). Nimbus 10.9.1 gelir, jjwt yok.
  - `app.jwt.*` (issuer kitapsepeti-user-service, access 15m, refresh 14d) → `security/JwtProperties`
    (`RsaKeyConfig` üzerinde `@EnableConfigurationProperties`).
  - `config/RsaKeyConfig`: RSAKey (kid = RFC 7638 thumbprint), JWKSet, JwtEncoder, JwtDecoder (RS256 +
    issuer + timestamp; saat = Clock bean'i). `config/ClockConfig`: `Clock.systemUTC()` (@ConditionalOnMissingBean).
  - `service/JwtService.issueAccessToken(User)` → `AccessToken(value, expiresAt)`; claims: iss, sub, role, iat, exp, jti.
  - `controller/JwksController`: `GET /.well-known/jwks.json` → yalnızca public JWKS.
  - Spring context'li testler `@ActiveProfiles("test")` → `src/test/resources/application-test.yml` test anahtarları.
  - `http.oauth2ResourceServer` henüz AÇIK DEĞİL (9. adım).

- Global hata altyapısı (RFC 9457 ProblemDetail, 25 test yeşil):
  - `exception/ErrorCode`: status + log seviyesi (slf4j `Level`) + genel İngilizce detail; tek kaynak.
    Spec'teki listeye ek olarak `NOT_ACCEPTABLE(406)` eklendi.
  - `ApiException` (abstract) + `EmailAlreadyExists`, `InvalidCredentials`, `InvalidRefreshToken`, `ResourceNotFound`.
  - `ProblemDetails`: ortak yapı (`code` + `instance`=istek yolu) ve log satırı `METHOD path -> CODE`;
    stack trace yalnızca ERROR seviyesinde. Exception mesajı / gövde / rejectedValue ASLA loglanmaz.
  - `GlobalExceptionHandler extends ResponseEntityExceptionHandler`: Spring MVC exception'ları
    `handleExceptionInternal` override'ında code+genel detail alır; DataIntegrityViolation → 409 CONFLICT
    (logda yalnızca Hibernate constraint adı); catch-all → 500. `AccessDeniedException`/`AuthenticationException`
    advice'ta yeniden fırlatılır → 401/403 kararını ExceptionTranslationFilter verir.
  - `security/ProblemDetailAuthenticationEntryPoint` (401) ve `ProblemDetailAccessDeniedHandler` (403):
    Boot'un `tools.jackson.databind.json.JsonMapper` bean'i ile yazar (Boot bu mapper'a ProblemDetail mixin'ini ekler).
  - SecurityConfig'e `@EnableMethodSecurity` eklendi. `ClockConfig`'ten `@ConditionalOnMissingBean` kaldırıldı.
  - Test-only `src/test/.../exception/ExceptionTestController` (`/test/exceptions/**`) component scan ile tüm
    `@SpringBootTest` context'lerine girer (tek context, tek container).
  - Açık konu: Hibernate `org.hibernate.orm.jdbc.error` logger'ı SQL hata mesajını (örn. `Duplicate entry '<email>'`)
    WARN seviyesinde kendisi yazıyor.
  - PowerShell 5.1: 401 gövdesi `$_.ErrorDetails.Message` içinde gelir (yanıt stream'i boş olabilir).

## Sonraki adımlar
- AuthService/DTO/controller (register, login, refresh), refresh token, `oauth2ResourceServer` (9. adım), CORS.
- Yeni servisler eklendikçe kök POM kontrol listesini uygula (bkz. systemPatterns.md).
