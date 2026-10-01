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

- OpenAPI 3 dokümantasyonu (71 test yeşil; commit `edacb78`):
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

- Docker image + Compose + Actuator + sözleşme drift testi (80 test yeşil, henüz commit edilmedi):
  - `OpenApiContractTest`: `/v3/api-docs` ↔ `docs/api/user-service.openapi.json` anlamsal (JsonNode) karşılaştırma,
    farklı JSON Pointer yollarını listeler. Yeniden üretim:
    `.\mvnw.cmd -pl user-service test "-Dtest=OpenApiContractTest" "-Dopenapi.contract.update=true"`
    (Maven `-D` surefire JVM'ine geçiyor). Yazım biçimi (2 boşluk, `"ad": değer`, LF) node `JSON.stringify(d,null,2)` ile birebir.
  - `OpenApiConfig`: `servers: [{url: "/"}]` sabit — aksi halde springdoc isteğin host:port'unu yazar (test `http://localhost`,
    gerçek `:8081`) ve drift testi hep kırılır.
  - Actuator: yalnızca `health` expose, `show-details: never`, `probes.enabled`, liveness = `livenessState`,
    readiness = `readinessState, db` (RabbitMQ yok), `endpoints.web.discovery.enabled: false` (`/actuator` 404).
    SecurityConfig permitAll'a `GET /actuator/health`, `/actuator/health/**`. Kök `/actuator/health` yanıtı
    `{"status","groups"}` (Boot grup adlarını listeler; kapatma ayarı yok).
  - Boot 4 health paketleri: `org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups`,
    `org.springframework.boot.health.registry.HealthContributorRegistry` (modül `spring-boot-health`).
  - `user-service/Dockerfile` (context = kök): temurin 21 jdk build (go-offline katmanı + `/root/.m2` cache mount) →
    `jarmode=tools extract --layers --launcher` → temurin 21 jre runtime, `app` 10001:10001, ENTRYPOINT
    `java org.springframework.boot.loader.launch.JarLauncher` (manifest Main-Class ile doğrulandı). Image 594 MB (base JRE 459 MB).
    JRE image'ında curl/wget var → compose healthcheck `curl -fsS`.
  - Compose `user-service`: env yalnızca gerekenler (env_file YOK), JWT anahtarları compose secrets → `/run/secrets/*`.
    Docker Desktop'ta secret dosyaları root:root 0777 bağlanır (non-root okur). Linux host'ta host dosya izni geçerli olur.
  - `.gitattributes` (mvnw/`*.sh` LF, mvnw.cmd CRLF), `.dockerignore` (sırlar, target, docs, memory-bank, src/test).
  - DB kesintisi (aynı adım, sonradan): `spring.datasource.hikari.connection-timeout: 5000` (ms; `5s` yazımı
    HikariDataSource'un long alanına BAĞLANMAZ, context açılmaz). DB kapalıyken readiness ~5 s'de 503 DOWN, login ~5 s'de
    500 INTERNAL_ERROR ProblemDetail. `OutboxRelay.poll()` `DataAccessException | TransactionException` → tek satır WARN
    `Outbox poll skipped, database unavailable: <SimpleName>`; diğerleri scheduler error handler'ına (ERROR + stack).
    Test: `OutboxRelayDatabaseFailureTest` (context'siz; stub transaction manager + Mockito repository, OutputCapture).
    DB dönünce restart gerekmeden healthy (~10 s).
  - Kalan log gürültüsü (dokunulmadı): `DataSourceHealthIndicator` her health çağrısında WARN + ~180 satır stack trace;
    Hikari `ProxyConnection` kesinti anında bir kez WARN + stack; 500'lerde `GlobalExceptionHandler` ERROR + stack (tasarım gereği).
    Readiness DB yokken ~5,0 s sürüyor; compose healthcheck `curl --max-time 4` bu yüzden 503 yerine timeout (28) ile fail eder.

- Adminer (opsiyonel geliştirme aracı, henüz commit edilmedi): compose'da `adminer` servisi, `profiles: ["tools"]` →
  normal `docker compose up -d` açmaz. Port yalnızca `127.0.0.1:8090`; `ADMINER_DEFAULT_SERVER: mysql`, parola/env_file yok.
  Yalnızca bakmak için; şema değişikliği Flyway ile (Adminer'dan tablo değiştirmek `ddl-auto: validate`'i kırar).
  Kökte README yok; kullanım notu burada ve techContext'te.

- catalog-service iskeleti (henüz commit edilmedi; tablo/entity/security/amqp/springdoc/actuator YOK):
  - Kök POM `<modules>`'e `catalog-service` eklendi; başka kök POM değişikliği gerekmedi (yeni kütüphane ailesi yok,
    Lombok zaten kökte). Servis POM'u: webmvc, data-jpa, validation, flyway + flyway-mysql, mysql-connector-j (runtime);
    test: user-service'in MySQL Testcontainers seti (RabbitMQ/amqp-test/awaitility/security-test hariç). Surefire `*IT` include'u aynı.
  - Ana sınıf `com.kitapsepeti.catalog.CatalogServiceApplication` (exclude yok; security bağımlılığı yok). Port 8082.
  - `application.yml` user-service kalıbı: `CATALOG_DB_HOST/PORT/USER/PASSWORD`, `catalog_db`, Hikari `connection-timeout: 5000`
    (ms; `5s` bağlanmaz), validate, open-in-view false, UTC, Flyway `classpath:db/migration` (şimdilik yalnızca `.gitkeep`).
    Açılışta Flyway `No migrations found` WARN'ı ve `catalog_db`'de boş `flyway_schema_history` beklenen durum.
  - Test: `TestcontainersConfiguration` (yalnızca MySQL), `CatalogServiceApplicationTests` (`@ActiveProfiles("test")`),
    `application-test.yml` datasource username/password sabit (.env'e bağımlılık yok).
  - DB çoklu şema yöntemi kararlaştırıldı: `infra/mysql/init/NN-<servis>-db.sh` (LF, `.gitattributes` `*.sh eol=lf`),
    compose mysql'e `./infra/mysql/init:/docker-entrypoint-initdb.d:ro` + `CATALOG_DB_USER/PASSWORD` env.
    Script gövdesi `( set -eu ... )` alt kabukta: entrypoint çalıştırılabilir olmayan .sh'leri source eder (Linux host'ta
    git modu 644), `set -u` entrypoint'e sızmasın. Root parolası `MYSQL_PWD` ile; SQL idempotent (IF NOT EXISTS).
    Parola SQL'e tek tırnakla gömülür → yalnızca alfanümerik.
  - Entrypoint init klasörünü yalnızca BOŞ volume'da çalıştırır; mevcut volume'da elle:
    `docker compose exec mysql sh /docker-entrypoint-initdb.d/10-catalog-db.sh` (iki kez çalıştırıldı, hatasız).
  - Yetki doğrulandı: catalog_svc yalnızca catalog_db görür, `user_db`/`CREATE DATABASE x` → 1044; user_svc catalog_db görmez.
  - PowerShell 5.1: `docker compose exec mysql sh -c '...'` içindeki çift tırnaklar native argümana geçerken kaybolur.
    Parolayı göstermeden bağlanmak için: `"SQL;" | docker compose exec -T mysql sh -c 'MYSQL_PWD=$CATALOG_DB_PASSWORD mysql -ucatalog_svc'`.
  - Kökte hâlâ README yok (port haritası eklenmedi). Portlar: user-service 8081, catalog-service 8082, adminer 8090.

- catalog-service V1 şeması (henüz commit edilmedi; entity/repository YOK): `V1__create_catalog_tables.sql` →
  publishers, authors, categories (parent_id self-FK RESTRICT), books, book_authors, book_categories (book CASCADE,
  diğer taraf RESTRICT), stock_reservations (book RESTRICT, order_id FK'sız, uk (order_id, book_id)), outbox (user-service ile aynı).
  books: `version` (JPA @Version için), status küçük harf ('draft'|'published'|'archived'), 6 CHECK.
  Yerel `catalog_db`'ye uygulandı (flyway_schema_history rank 1). Bundan sonra V1 DEĞİŞTİRİLMEZ.
  - MySQL FK için index yoksa FK adıyla otomatik index açar (örn. books'ta `KEY fk_books_publisher`); entity'de/validate'te sorun değil.
  - `ck_books_stock_non_negative` mantıken gereksiz: `reserved >= 0` ve `reserved <= stock` zaten `stock >= 0` demek.
    Negatif stokta MySQL yalnızca bir ihlal raporlar ve CHECK'leri ada göre (alfabetik) değerlendirir →
    `ck_books_reserved_le_stock` görünür. Kısıt yine de şemada tutuldu (spec).
  - ÖNEMLİ: CHECK ihlali MySQL'de SQLSTATE HY000 / hata 3819. Spring JdbcTemplate bunu sınıflandıramaz →
    `UncategorizedSQLException` (DataIntegrityViolationException DEĞİL). FK (1451/1452) ve UNIQUE (1062) doğru çevrilir
    (`DataIntegrityViolationException` / `DuplicateKeyException`). JPA yolu için aşağıdaki entity notuna bak.
  - Test: `schema/CatalogSchemaConstraintsTest` (`@JdbcTest` + `@AutoConfigureTestDatabase(NONE)` + Testcontainers,
    paket `org.springframework.boot.jdbc.test.autoconfigure`); her test transaction'da, geri alınır. Kısıt adı mesajda doğrulanır.
  - `db/migration/.gitkeep` artık gereksiz; adım kapsamı dışında kaldığı için silinmedi.
  - Docker Desktop oturum başında kapalı olabiliyor: Testcontainers "Could not find a valid Docker environment" → 
    `Start-Process "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"`.

- catalog-service entity + repository (henüz commit edilmedi; servis/controller/security YOK):
  - `entity/`: Publisher, Author, Category (parent LAZY, children yok), Book, StockReservation, OutboxEvent (user-service'in
    birebir kopyası), BookStatus/ReservationStatus + `AttributeConverter` (küçük harf, `@Convert` açıkça; autoApply yok).
  - user-service kalıbı aynen: `@UuidGenerator(VERSION_7)`, `@CreationTimestamp`/`@UpdateTimestamp` + `Instant`,
    `@Getter` + yalnızca değişebilir alanlarda `@Setter`, `@NoArgsConstructor(PROTECTED)` + zorunlu alanlı public ctor,
    Java'da default değerler (status, currency 'TRY', stok/rezerv 0). equals/hashCode OVERRIDE EDİLMEZ (Object kimliği):
    hashCode persist öncesi/sonrası sabit (testli). Bedeli: farklı persistence context'lerden gelen aynı satır eşit sayılmaz.
  - Book: `@Version Long version` (yeni entity'de null → insert'te 0; Spring Data isNew de buna bakar),
    `description` için `columnDefinition = "TEXT"` (validate TEXT ↔ varchar(255) uyuşmazlığı riskine karşı),
    `@ManyToMany(LAZY)` Set<Author>/Set<Category> + `@JoinTable`, alan tanımında `new HashSet<>()`. Tek yönlü.
  - StockReservation: book/orderId/quantity/expiresAt `updatable = false`, yalnızca status setter'lı.
  - `BookRepository extends JpaRepository, JpaSpecificationExecutor`; `findWithDetailsById` `@EntityGraph(publisher, authors,
    categories)` — iki koleksiyon Set olduğu için MultipleBagFetchException yok.
  - JPA hata çevirisi (Hibernate 7 + MySQL 8.4, save+flush): CHECK/UNIQUE/FK üçü de TAM OLARAK
    `DataIntegrityViolationException` (UNIQUE için `DuplicateKeyException` DEĞİL). `getCause()` =
    `org.hibernate.exception.ConstraintViolationException`, `getKind()` = CHECK / UNIQUE / FOREIGN_KEY,
    `getConstraintName()` = `ck_books_reserved_le_stock` / `publishers.uk_publishers_slug` (MySQL tablo önekli) /
    `fk_books_publisher`. En özel neden: CHECK → `java.sql.SQLException`, diğerleri `SQLIntegrityConstraintViolationException`.
    → Hata işleyicisi `ConstraintViolationException.getKind()` + kısıt adıyla eşleme yapabilir; JdbcTemplate kullanılırsa
    3819 yine `UncategorizedSQLException` olur.
  - Optimistic lock: eski versiyonla flush → `ObjectOptimisticLockingFailureException`, kök neden
    `org.hibernate.StaleStateException` (StaleObjectStateException DEĞİL), mesajda `where id=? and version=?`.
  - Testler: `repository/CatalogRepositoryTest` (13, transactional) ve `repository/BookTransactionBoundaryTest` (2,
    `@Transactional(NOT_SUPPORTED)` + TransactionTemplate; REQUIRES_NEW ile eşzamanlı güncelleme; `@AfterEach` JDBC temizliği —
    aynı context'i paylaşan diğer testler artık veri görmesin). BINARY(16) JDBC'de `UUID_TO_BIN(?)` / `BIN_TO_UUID` ile.
  - `db/migration/.gitkeep` silindi.
  - (Sonradan) İskelet, V1 ve entity/repository adımları commit + push edildi (`b32141e`, `ed3a62c`, `afce68a`).

- catalog-service hata altyapısı + OAuth2 Resource Server (56 test yeşil; henüz commit edilmedi; user-service'e dokunulmadı,
  ortak modül YOK — Cart servisi gelince ortak modüle çıkarılacak):
  - Bağımlılıklar: `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, test `spring-boot-starter-security-test`.
  - user-service'ten KOPYA (paket `com.kitapsepeti.catalog`): `exception/` ErrorCode, ApiException, ResourceNotFoundException,
    ProblemDetails, DbConstraints, GlobalExceptionHandler; `security/` BearerChallenge, ProblemDetailResponses, entry point,
    access denied handler. `CatalogServiceApplication` → `exclude = UserDetailsServiceAutoConfiguration.class`.
  - ErrorCode: user-service'in genel kodları (aynı status/log seviyesi) + SLUG_ALREADY_EXISTS, ISBN_ALREADY_EXISTS,
    RESOURCE_IN_USE, CONCURRENT_MODIFICATION (hepsi 409/INFO). Kullanıcıya özel kodlar alınmadı.
  - `DbConstraints.classify(ex)` → `Violation(code, constraint, kind)`: Hibernate `ConstraintViolationException` neden zincirinden,
    ad "tablo." öneki atılıp küçük harfe normalize. UNIQUE uk_{publishers,authors,categories}_slug → SLUG_ALREADY_EXISTS,
    uk_books_isbn → ISBN_ALREADY_EXISTS; FOREIGN_KEY + MySQL 1451 → RESOURCE_IN_USE (`violation.getErrorCode()`, JDBCException);
    1452 / CHECK / bilinmeyen UNIQUE / diğer → CONFLICT. Log notu `constraint=<ad>, kind=<KIND>` (değer yok).
  - `OptimisticLockingFailureException` (üst sınıf; ObjectOptimistic... dahil) → CONCURRENT_MODIFICATION.
  - `config/SecurityConfig`: kural sırası GET /api/books/** + /api/categories/** permitAll → /error permitAll →
    /api/admin/** ADMIN → /internal/** denyAll → anyRequest authenticated. Özel BearerTokenResolver, public GET'lerde
    (aynı PathPatternRequestMatcher kuralları) Authorization'ı yok sayar → bozuk/expired token public okumayı 401'e düşürmez.
    `@EnableMethodSecurity` YOK (URL kuralları yeterli).
  - `config/JwtDecoderConfig`: kendi `JwtDecoder` bean'i = `NimbusJwtDecoder.withJwkSetUri(jwk-set-uri).jwsAlgorithm(RS256)`
    + `JwtValidators.createDefaultWithIssuer(app.jwt.issuer)` (exp/nbf 60 sn tolerans + iss). jwk-set-uri
    `OAuth2ResourceServerProperties`'ten (Boot 4 paketi `org.springframework.boot.security.oauth2.server.resource.autoconfigure`),
    issuer `security/JwtProperties` (`app.jwt.issuer`, @Validated @NotBlank). JWKS istemcisi RestTemplate connect 2 s / read 3 s.
    issuer-uri/OIDC discovery KULLANILMAZ (issuer URI değil). JWKS lazy: açılışta istek yok; user-service kapalıyken başlar.
  - Bilinen davranış (düzeltilmedi): JWKS erişilemezken token'lı korumalı istek → Security 7 `AuthenticationServiceException`'ı
    yeniden fırlatır → Boot varsayılan `/error` JSON'u ile **500** (ProblemDetail değil, ERROR + stack trace). Token'sız/public
    istekler etkilenmez. Düzeltme seçeneği: resource server'a failure handler + 503 kodu.
  - user-service token'larında `typ` yok; `createDefaultWithIssuer` bunu reddetmiyor (uçtan uca doğrulandı).
  - Testler: `support/TestJwt` (user-service test anahtarıyla imza, thumbprint kid; yabancı anahtar, alg none),
    `support/JwksServer` (JDK `com.sun.net.httpserver.HttpServer`, loopback rastgele port, istek sayacı), `ApiTestSupport`
    (statik JWKS sunucusu + `@DynamicPropertySource`, tablo temizliği), `security/SecurityRulesTest` (14),
    `security/JwksFlowTest` (1; ayrı context, açılışta 0 istek → ilk doğrulamada 1 → önbellek), `exception/GlobalExceptionHandlerTest` (11,
    OutputCapture). Test controller'ları yalnızca src/test: `security/SecurityProbeController`, `exception/ErrorProbeController` (`/test/errors/**`).
  - Test anahtarı `catalog-service/src/test/resources/jwt/` (user-service'ten kopya); kök `.gitignore`'a
    `!catalog-service/src/test/resources/jwt/*.pem` eklendi.
  - Uçtan uca (compose user-service): USER token → /api/admin/x 403; role=ADMIN + yeniden login → 404; token'sız /api/books → 404.

## Sonraki adımlar
- catalog-service: servis + API (admin CRUD, public okuma), JWKS erişilemezken 500 davranışı kararı,
  springdoc, actuator, Dockerfile + compose servisi (`USER_SERVICE_JWKS_URI` compose'ta user-service adına).
- Docker adımının commit'i (kullanıcı isteyince), logout, e-posta/parola değiştirme, consumer servisler, CORS.
- Açık konular: DataSourceHealthIndicator stack trace gürültüsü; CI pipeline yok (drift testi yalnızca yerel `mvnw test`'te).
- Açık konu: outbox'ta yayınlanmış satırların temizliği (retention) yok; bilinmeyen event_type kuyruğun başını tıkar.
- Yeni servisler eklendikçe kök POM kontrol listesini uygula (bkz. systemPatterns.md).
