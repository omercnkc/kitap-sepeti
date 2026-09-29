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

- Kayıt / giriş / refresh akışı (38 test yeşil):
  - `controller/AuthController`: POST `/api/auth/register` (201), `/login`, `/refresh` (200) → `TokenResponse`
    (`Cache-Control: no-store`). Entity dönmez.
  - DTO'lar `dto/request` + `dto/response` (record); parola/token taşıyan tüm record'larda `toString()` maskeli
    (`AccessToken`, `RotationResult` dahil). `validation/Utf8MaxBytes(72)`: bcrypt 72 byte sınırı (`@Size` karakter sayar).
  - `AuthService`: e-posta `trim().toLowerCase(Locale.ROOT)`; register `existsByEmail` + `saveAndFlush` (yarışta
    `uk_users_email` → 409, `exception/DbConstraints` ile) + outbox `UserRegistered` (`service/event/UserRegisteredEvent`,
    eventVersion 1); login: kayıtsız e-postada dummy bcrypt (timing), >72 byte parola bcrypt'e verilmez → 401;
    SUSPENDED yalnızca doğru parolada 403. `refresh()` BİLEREK transactional değil (rotate'in noRollbackFor'u korunur).
  - `RefreshTokenService`: 32 byte SecureRandom → Base64URL ham token; DB'de SHA-256 hex. `rotate`:
    atomik `revokeIfActive` UPDATE; 0 satır = tekrar kullanım → `revokeAllActiveByUserId` + WARN (yalnızca userId).
  - `OutboxService.append`: `Propagation.MANDATORY`, payload Boot `JsonMapper` ile JSON.
  - `ErrorCode.ACCOUNT_SUSPENDED` (403, WARN). `logging.level.org.hibernate.orm.jdbc.error: ERROR`.
  - PowerShell 5.1 notu: `docker compose exec mysql` ile SQL'i `-e "..."` yerine stdin'den ver (tırnaklar bozuluyor).

- Resource Server + `/api/me` + adres uçları (57 test yeşil, henüz commit edilmedi):
  - `SecurityConfig`: `oauth2ResourceServer().jwt()` mevcut `JwtDecoder` bean'i ile; `JwtAuthenticationConverter`
    (claim `role`, prefix `ROLE_`, principal `sub`). Özel `BearerTokenResolver` `/api/auth/**` altında token okumaz
    (bayat `Authorization` başlığı login/refresh'i 401'e düşürmesin). permitAll listesi değişmedi.
  - `security/BearerChallenge`: token yoksa `WWW-Authenticate: Bearer`, geçersizse `Bearer error="invalid_token"`
    (entry point + advice'taki 401). `ErrorCode.UNAUTHORIZED` artık INFO.
  - `security/CurrentUserId` = `@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(subject)")`.
    Kullanıcı id'si YALNIZCA buradan; path/body'de userId yok.
  - `UserService.requireActiveUser`: silinmiş kullanıcı (token hâlâ geçerli) → `UnauthorizedException` (401),
    SUSPENDED → 403. `MeController` GET/PATCH `/api/me`.
  - `AddressService`: ilk adres otomatik varsayılan; `isDefault:true` önce toplu `clearDefault` (JPQL) sonra insert/update;
    varsayılanı `false` yapmak → `DEFAULT_ADDRESS_REQUIRED` (409); varsayılan silinirse en yeni kalan terfi eder.
    DB'de `uk_addresses_default_owner` tek varsayılanı garanti eder.
  - Tuzaklar: (1) toplu UPDATE yüklü entity'yi güncellemez → zaten varsayılan adrese `clearDefault` UYGULANMAZ
    (mutasyonla doğrulandı: koruma kalkınca test kırılıyor). (2) Hibernate flush sırası insert→update→delete;
    delete'ten sonra açık `flush()` var (mutasyonda test kırılmadı çünkü sonraki sorgunun AUTO flush'ı delete'i yazıyor).
  - `AddressController`: GET liste, GET `/{id}` (spec dışı ek), POST 201 + Location, PATCH, DELETE 204.
    Mapper'lar elle (`mapper/`), MapStruct yok. `validation/NullOrNotBlank`: PATCH'te null=değiştirme, boş=400.
  - Testler: `ApiTestSupport` (ortak base, tabloları temizler), `ResourceServerSecurityTest`, `MeControllerTest`,
    `AddressControllerTest`.
  - PowerShell 5.1: BOM'suz `.ps1` ANSI okunur → script içindeki Türkçe literal'ler bozulur (sunucu değil).

- RabbitMQ + outbox worker (64 test yeşil; commit `059a8f1`):
  - `docker-compose.yml`: `rabbitmq:4-management` (`kitapsepeti-rabbitmq`, 5672 + 127.0.0.1:15672, volume
    `rabbitmq_data`, sabit `hostname` — RabbitMQ veriyi node adına göre saklar). `.env`: `RABBITMQ_USER/PASSWORD`.
  - Bağımlılıklar: `spring-boot-starter-amqp` (+ test: `spring-boot-starter-amqp-test`,
    `org.testcontainers:testcontainers-rabbitmq` → `org.testcontainers.rabbitmq.RabbitMQContainer`, `awaitility`).
  - `application.yml`: `spring.rabbitmq.*` (`publisher-confirm-type: correlated`, `connection-timeout: 5s`),
    `app.outbox.*` → `outbox/OutboxProperties` (`RabbitConfig` üzerinde `@EnableConfigurationProperties`).
    Test profilinde `app.outbox.enabled: false`.
  - `config/RabbitConfig`: durable `TopicExchange kitapsepeti.events`; açılışta `AmqpAdmin.initialize()` (broker
    kapalıysa WARN, uygulama açılır). Kuyruk tanımı YOK (consumer'ın işi).
  - `outbox/OutboxPublisher`: tek satır → mesaj (messageId=outbox id, type, JSON/UTF-8, timestamp=created_at,
    persistent, header aggregateType/aggregateId) + `CorrelationData` future ile confirm bekler; nack/timeout/bağlantı →
    `OutboxPublishException`. `outbox/EventRoutingKeys`: `UserRegistered` → `user.registered`.
  - `outbox/OutboxRelay` (`@ConditionalOnProperty app.outbox.enabled`), `config/SchedulingConfig` (`@EnableScheduling`,
    aynı koşul). Tur = `TransactionTemplate`; `OutboxRepository.lockUnpublishedBatch` (native `FOR UPDATE SKIP LOCKED`);
    ilk hatada `break` + WARN (id, eventType, hata sınıfı), başarılılar commit.
  - `GlobalExceptionHandler`: `ConstraintViolationException` → 400 VALIDATION_FAILED, errors[{field=son path düğümü, message}].
  - Testler: `OutboxRelayIT` (worker açık, `FaultInjectingPublisher` @Primary test double), `OutboxSkipLockedTest`
    (worker kapalı paylaşılan context), `EventRoutingKeysTest`. Surefire `**/*IT.java`'yı da koşar (user-service pom).
  - Doküman: `docs/events/user-registered.md`.
  - PowerShell tuzağı: tr-TR kültüründe `-match '[A-Z]'` "I" harfini eşlemez (Türkçe I); `-cmatch` kullan.
  - Spring AMQP, correlated confirm'de mesaja `spring_returned_message_correlation` header'ı ekler (dokümanda not var).

- OpenAPI 3 dokümantasyonu (71 test yeşil, henüz commit edilmedi; yalnızca anotasyon/doküman, davranış aynı):
  - `springdoc-openapi-starter-webmvc-ui` 3.1.1 (kök POM `springdoc.version` + dependencyManagement). 3.x = Boot 4
    hattı; 3.1.1'in parent'ı `spring-boot-starter-parent` 4.1.0. Çıktı OpenAPI **3.1.0**.
  - `application.yml` `springdoc.*`: `paths-to-match: /api/**, /.well-known/**` (test controller dışarıda),
    `default-produces-media-type: application/json`, `writer-with-order-by-keys: true` (deterministik çıktı),
    `api-docs`/`swagger-ui.enabled: ${SPRINGDOC_ENABLED:true}`. `/v3/api-docs/**` ve `/swagger-ui/**` zaten permitAll'daydı.
  - `app.version: "@project.version@"` → Maven resource filtering (Boot parent `@..@` delimiter) → `info.version`.
  - `config/OpenApiConfig`: global `bearerAuth` (HTTP bearer JWT); `Problem` (+ `FieldError`) şeması, `code` enum'u
    `ErrorCode.values()`'tan; `OpenApiCustomizer` standart hataları ekler (korumalıya 401, gövdeliye 400, `{id}`'liye 404,
    hepsine 500) — operasyonda aynı kod varsa dokunmaz. Public uçlar (Auth, Keys) sınıf seviyesinde boş `@SecurityRequirements`
    → dokümanda `security: []`. Profile/Addresses'te sınıf seviyesi `@ApiResponse` 403.
  - Tüm operasyonlarda açık `operationId` (aksi halde `get`, `get_1`...). DTO'larda `@Schema`; parolalar `WRITE_ONLY`.
  - Sözleşme dosyası: `docs/api/user-service.openapi.json` (çalışan uygulamadan `GET /v3/api-docs`, 2 boşluk girinti,
    BOM'suz UTF-8, sonda newline). Uç/DTO değişince yeniden üretilmeli. 7 path / 11 operasyon.
  - Test: `config/OpenApiDocsTest` (7 test; JsonPath).

## Sonraki adımlar
- OpenAPI adımının commit'i (kullanıcı isteyince), logout, e-posta/parola değiştirme, consumer servisler, CORS.
- Açık konu: sözleşme dosyası elle üretiliyor; CI'da `/v3/api-docs` ile diff kontrolü yok.
- Açık konu: outbox'ta yayınlanmış satırların temizliği (retention) yok; bilinmeyen event_type kuyruğun başını tıkar.
- Yeni servisler eklendikçe kök POM kontrol listesini uygula (bkz. systemPatterns.md).
