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
  - JWKS erişilemezken 500 sorunu sonraki adımda 503 ile çözüldü (aşağıya bak).
  - user-service token'larında `typ` yok; `createDefaultWithIssuer` bunu reddetmiyor (uçtan uca doğrulandı).
  - Testler: `support/TestJwt` (user-service test anahtarıyla imza, thumbprint kid; yabancı anahtar, alg none),
    `support/JwksServer` (JDK `com.sun.net.httpserver.HttpServer`, loopback rastgele port, istek sayacı), `ApiTestSupport`
    (statik JWKS sunucusu + `@DynamicPropertySource`, tablo temizliği), `security/SecurityRulesTest` (14),
    `security/JwksFlowTest` (1; ayrı context, açılışta 0 istek → ilk doğrulamada 1 → önbellek), `exception/GlobalExceptionHandlerTest` (11,
    OutputCapture). Test controller'ları yalnızca src/test: `security/SecurityProbeController`, `exception/ErrorProbeController` (`/test/errors/**`).
  - Test anahtarı `catalog-service/src/test/resources/jwt/` (user-service'ten kopya); kök `.gitignore`'a
    `!catalog-service/src/test/resources/jwt/*.pem` eklendi.
  - Uçtan uca (compose user-service): USER token → /api/admin/x 403; role=ADMIN + yeniden login → 404; token'sız /api/books → 404.
  - Commit `c6cee96` (+ memory-bank `a46cee5`), push edildi.

- catalog-service JWKS kesintisi → 503 (65 test yeşil; henüz commit edilmedi):
  - `ErrorCode.AUTHENTICATION_UNAVAILABLE` (503, WARN). Retry-After YOK (süre bilinmiyor), WWW-Authenticate YOK.
  - `security/ProblemDetailAuthenticationFailureHandler`: `AuthenticationServiceException` (JWKS'e ulaşılamaması / anahtar alınamaması;
    JwtAuthenticationProvider BadJwtException dışındaki JwtException'ları buna çevirir) → 503 ProblemDetail; diğer
    AuthenticationException'lar → mevcut entry point (401 + WWW-Authenticate aynen). Log tek WARN satırı:
    `GET /api/admin/x -> AUTHENTICATION_UNAVAILABLE (cause=ConnectException)` (kök neden kısa adı; mesaj JWKS URL'si içerdiği için yazılmaz).
  - Bağlama: Security 7.1 resource server DSL'inde failure handler ayarı YOK (yalnızca entryPoint/accessDeniedHandler/bearerTokenResolver/
    authenticationManagerResolver). `OAuth2ResourceServerConfigurer.configure` BearerTokenAuthenticationFilter'ı entry point ayarından
    SONRA `postProcess`'ten geçirir → `rs.withObjectPostProcessor(ObjectPostProcessor<BearerTokenAuthenticationFilter>)` içinde
    `setAuthenticationFailureHandler`. ObjectPostProcessor generic metotlu → lambda değil anonim sınıf.
  - Nimbus/Spring JWKS hatayı önbelleğe almaz: kesinti sonrası ilk istek yeniden çeker (test + uçtan uca doğrulandı).
    Anahtar alındıktan sonra JWKS kapanırsa önbellek süresi içinde doğrulama sürer.
  - Testler: `security/JwksOutageTest` (4; sabit port, sıralı adımlar: 503 + tek WARN → token'sız 401 / public 200 → sunucu açılınca 200 →
    kapanınca önbellekten 200), `exception/DbConstraintsTest` (4, context'siz), SecurityRulesTest'e bozuk imza testi.
    `support/JwksServer`: `start(json, port)`, `freePort()`, statik `jwkSetUri(port)`.
  - Uçtan uca: user-service kapalı + catalog yeniden başlatılmış → 503 AUTHENTICATION_UNAVAILABLE; user-service healthy → aynı token 404.
  - Commit `320d8fc` (+ memory-bank `5676f4c`), push edildi.

- catalog-service public okuma uçları + local seed (89 test yeşil; henüz commit edilmedi; admin yazma/stok/outbox/OpenAPI YOK,
  metin arama YOK — Search Service'in işi):
  - Uçlar: GET `/api/books` (sayfalı liste), GET `/api/books/{id}` (yalnızca PUBLISHED), GET `/api/categories` (ağaç). Entity dönmez.
  - DTO'lar `dto/response` (record): PageResponse(items, page, size, totalElements, totalPages), Publisher/Author/CategoryRef(id, name, slug),
    BookSummaryResponse, BookDetailResponse (+ isbn, description, pageCount, publishedAt, categories), CategoryTreeResponse.
    `inStock = stock - reserved > 0`; stok/rezerv/version/status/timestamp yanıtta YOK. authors/categories Türkçe ada göre sıralı.
  - `dto/request/BookSearchRequest` (record, `@ModelAttribute` constructor binding): wrapper tipler + compact ctor default'ları
    (sort NEWEST, page 0, size 20); `@DecimalMin(0)` fiyatlar, `@Min(0)` page, size 1–50; sınıf seviyesi `validation/ValidPriceRange`
    (ihlal `minPrice` alanına yazılır). `BookSort` NEWEST/PRICE_ASC/PRICE_DESC/TITLE_ASC; `config/WebConfig` Converter'ı büyük/küçük
    harf duyarsız (Spring'in varsayılan enum çevirisi duyarlı). Her sıralamada ikincil `id`.
  - Hata kodları: query'de bozuk UUID/sayı/bilinmeyen sort → binding hatası → 400 VALIDATION_FAILED, errors[{field, "invalid value"}]
    (girilen değer yanıtta yok). Path'te bozuk UUID → TypeMismatch → 400 MALFORMED_REQUEST.
  - `service/BookQueryService` + `repository/BookSpecifications`: PUBLISHED + publisher eşitlik + author/category EXISTS alt sorgusu
    (JOIN değil → kopya satır yok, count basit). categoryId alt kategorileri kapsar (`service/CategoryForest`, bellekte; bilinmeyen id →
    boş sonuç). `BookRepository.findAll(Specification, Pageable)` override'ı `@EntityGraph("publisher")` (count sorgusuna uygulanmaz).
    `hibernate.default_batch_fetch_size: 50` → liste = 3 SQL (sayfa+publisher JOIN, count, yazar batch IN), yazar sayısından bağımsız.
  - `CategoryQueryService.tree()`: tek `findAll`, bellekte ağaç, her seviye Türkçe ada göre (`mapper/NameOrder.TURKISH` = tr Collator;
    `String.compareTo` Ç'yi E'den sonraya koyar).
  - Seed: `db/seed/R__dev_seed_catalog.sql` (repeatable), yalnızca `application-local.yml` (`spring.flyway.locations` + db/seed).
    Sabit UUID'ler `01920000-0000-7000-8000-000000000NNN` (yayınevi 1xx, yazar 2xx, kategori 3xx, kitap 4xx) + `INSERT ... AS new
    ON DUPLICATE KEY UPDATE`. 3 yayınevi, 5 yazar, 6 kategori (Edebiyat > Roman/Öykü, Bilim > Popüler Bilim, Çocuk), 14 kitap
    (11 published; 402 stock=reserved, 403 stock=0; 412-413 draft; 414 archived). Dosyadan silinen bağ (book_authors) yeniden çalıştırmada silinmez.
  - TUZAK: local'de seed uygulanmış DB varsayılan profilde açılınca Flyway "Detected applied migration not resolved locally: dev seed catalog"
    ile düşüyordu → `application.yml` `spring.flyway.ignore-migration-patterns: "*:future,repeatable:missing"` (versioned kontrolü aynen).
    `DevSeedMigrationTest` deseni application.yml'den okuyup doğrular.
  - Flyway aynı checksum'lı repeatable'ı yeniden çalıştırmaz; seed idempotentliği dosyanın doğrudan yeniden çalıştırılmasıyla test edilir
    (`ScriptUtils`; yerelde `docker cp` + container içinde `mysql --default-character-set=utf8mb4 < /tmp/seed.sql`).
  - Test profili: `hibernate.generate_statistics: true` (StatisticalLoggingSessionEventListener WARN'a çekildi). Testler:
    `controller/BookControllerTest` (20), `controller/CategoryControllerTest` (2), `seed/DevSeedMigrationTest` (2, Spring'siz, kendi container'ı).
  - PowerShell konsolu yanıt JSON'undaki Türkçe karakterleri `�` gösterir; baytlar doğru UTF-8 (C3 87 = Ç).
  - `spring-boot:run "-Dspring-boot.run.arguments=--logging.level..."` ve `LOGGING_LEVEL_ORG_FLYWAYDB` env ile Flyway DEBUG log'u gelmedi (çözülmedi).
  - Commit `297c9bc` (+ memory-bank `68841c7`), push edildi.

- catalog-service admin yazma uçları: yayınevi, yazar, kategori (128 test yeşil; commit `52194f4` + memory-bank `912215b`, push edildi):
  - `controller/admin/`: AdminPublisherController, AdminAuthorController (`/api/admin/{publishers,authors}`: GET liste, POST 201 + Location,
    GET/PATCH/DELETE `/{id}`), AdminCategoryController (+ `PUT /{id}/parent`). Yetki SecurityConfig'teki `/api/admin/**` ADMIN kuralından.
  - Servisler `@Transactional` (okumalar readOnly): PublisherAdminService, AuthorAdminService, CategoryAdminService. Ortak yardımcılar
    package-private: `Slugs.forCreate` (verilen slug ya da addan üretilen; boş/uzunsa InvalidFieldException "slug"), `AdminPaging`
    (Sort name asc, id asc). Liste `AdminPageRequest` (page >= 0, size 1–100, varsayılan 20).
  - `service/SlugGenerator.fromName`: Türkçe harfler açıkça (ç ğ ı İ ö ş ü + büyükleri), sonra NFD + birleşik işaretleri at (é → e; spec'e EK),
    `toLowerCase(ROOT)`, `[^a-z0-9]+` → "-", kenar "-" atılır. Harf/rakam yoksa "" döner.
  - DTO'lar: Create/Update{Publisher,Author,Category}Request (name compact ctor'da `strip()` → `@NotBlank`/`@NullOrNotBlank` + `@Size`
    kırpılmış değere uygulanır), MoveCategoryRequest(parentId; null/eksik = kök). `validation/Slug(max)` composed constraint
    (@Pattern + @Size, `@OverridesAttribute` ile max), `validation/NullOrNotBlank` (user-service kopyası).
  - PATCH: null = değiştirme; yalnızca ad değişirse slug korunur. Slug ön kontrolü `existsBySlug`; yarışta `uk_*_slug` → DbConstraints → aynı 409.
    Güncelleme sonrası `flush()` (güncel `updatedAt` yanıta girsin).
  - Silme: `delete` + `flush()` → FK 1451 servis içinde `DataIntegrityViolationException` → GlobalExceptionHandler → 409 RESOURCE_IN_USE
    (log: `constraint=fk_books_publisher | fk_book_authors_author | fk_categories_parent | fk_book_categories_category`). Ön sorgu YOK.
  - Kategori taşıma: `CategoryRepository.findAllForUpdate()` (`@Lock(PESSIMISTIC_WRITE)`, tüm satırlar FOR UPDATE) → `CategoryForest`
    (yeni `find(id)`); yeni üst `subtreeIds(id)` içindeyse 409 CATEGORY_CYCLE. Sıra: id yok 404 → üst yok 400 parentId → döngü 409.
    Kilit eşzamanlı iki taşımanın birbirini görmeden döngü kurmasını önler.
  - Yeni hata altyapısı: `ErrorCode.CATEGORY_CYCLE` (409, INFO), `SlugAlreadyExistsException`, `CategoryCycleException`,
    `InvalidFieldException(field, message)` → handler 400 VALIDATION_FAILED + `errors[{field, message}]` (değer yok).
  - Admin listesi DB collation'ıyla (`utf8mb4_0900_ai_ci`, aksan duyarsız) sıralanır; public ağaç Türkçe Collator ile. "Ç" admin listede C ile karışık sıralanır.
  - PATCH /categories gövdesindeki `parentId` sessizce yok sayılır (Jackson bilinmeyen alanı reddetmez); taşıma yalnızca PUT /parent.
  - Testler: `service/SlugGeneratorTest` (12), `controller/admin/AdminAccessTest` (3), `AdminPublisherControllerTest` (14),
    `AdminAuthorControllerTest` (4), `AdminCategoryControllerTest` (6).
  - Uçtan uca (compose user-service + local seed) a–g geçti; deneme kayıtları silindi, seed sayıları değişmedi. user_db'de e2e kullanıcıları kalıyor (önceki adımlardaki gibi).

- catalog-service kitap admin uçları + stok düzeltme + outbox olayları (177 test yeşil; commit `366c465`; RabbitMQ/relay,
  internal stok uçları (reserve/commit/release), OpenAPI YOK; migration'a dokunulmadı):
  - `controller/admin/AdminBookController` `/api/admin/books`: GET liste (`?status=draft|published|archived`, büyük/küçük harf duyarsız,
    tanınmayan → 400; sıra updatedAt desc, id desc; size 1–100), GET `/{id}` (her durum), POST (201 + Location, her zaman DRAFT + TRY,
    olay yok), PATCH `/{id}` (version zorunlu), POST `/{id}/publish`, POST `/{id}/archive` (200) ve DELETE `/{id}` (204) = aynı arşivleme
    (fiziksel silme yok), POST `/{id}/stock-adjustments {delta}`.
  - KURAL: `Book.stockQuantity/reservedQuantity` `@Column(updatable = false)`, setter YOK; yalnızca insert'te (ctor
    `Book(title, publisher, price, initialStock)`). Değişiklik yalnızca `BookRepository.adjustStock` (JPQL `@Modifying(flushAutomatically,
    clearAutomatically)`: `set stockQuantity = stockQuantity + :delta where id = :id and stockQuantity + :delta >= reservedQuantity`).
    JPQL bulk update versiyonu DEĞİŞTİRMEZ (bilinçli: stok admin formunun parçası değil). updated_at ise DEĞİŞİR: `@UpdateTimestamp`
    bulk UPDATE'te çalışmaz ama kolonun `ON UPDATE CURRENT_TIMESTAMP(6)` tanımı değer değiştiren her UPDATE'te DB saatini yazar
    (`BookStockColumnsTest.adjustStockRefreshesUpdatedAtThroughDatabaseDefault`; 0 satırlık ayarlama dokunmaz). 0 satır → existsById ? 409
    STOCK_BELOW_RESERVED : 404. `BookStockColumnsTest` Hibernate UPDATE SQL'inde stok kolonlarının olmadığını ve araya giren rezerv
    değişikliğinin ezilmediğini doğrular (mutasyonla kırıldığı görüldü).
  - Optimistic lock: istemci `version` ≠ entity → `StaleVersionException` (409 CONCURRENT_MODIFICATION), hiçbir şey yazılmaz;
    istek içi yarışı Hibernate `@Version` yakalar. Değiştiren işlemler (PATCH/publish/archive) kitabı `findForUpdateById`
    (`@Lock(PESSIMISTIC_WRITE)`) ile okur → eşzamanlı stok düzeltmesi bekler, olaydaki inStock/durum tutarlı.
  - Olaylar: `service/OutboxService` (user-service kopyası, MANDATORY), `service/BookEventFactory`, payload record'ları
    `service/event/BookUpsertedEvent` / `BookRemovedEvent` (eventVersion 1). aggregate_type `book` (user-service `user` gibi küçük harf),
    event_type `BookUpserted` / `BookRemoved` (PascalCase). Routing key'ler `book.upserted` / `book.removed` (relay sonraki adımda
    eklendi, aşağıya bkz.). V1 SQL yorumundaki 'Book'/'BookPublished' örnekleri eski; migration değiştirilmedi.
    BookUpserted: publish (DRAFT/ARCHIVED → PUBLISHED), PUBLISHED kitapta her PATCH, PUBLISHED kitapta inStock değişen stok düzeltmesi.
    BookRemoved: PUBLISHED → ARCHIVED. Zaten yayında/arşivde = değişiklik ve olay yok. publishedAt yalnızca null ise atanır.
    Payload'da stok/rezerv/version/status YOK; priceAmount metin ("149.90"), `categoryIdsWithAncestors` (`CategoryForest.withAncestors`).
    PATCH'te olay flush'tan ÖNCE yazılır: flush'taki ISBN çakışması outbox satırını da geri alır (testte `insert into outbox` SQL'i görülüyor).
  - inStock geçişi: güncellenmiş satırdan `available` ve `available - delta` karşılaştırılır (satır kilitli; ayrı ön okuma yok).
  - Yayın koşulları: ≥1 yazar, ≥1 kategori, fiyat > 0; değilse 409 BOOK_NOT_PUBLISHABLE, detail "... Missing: at least one author, ..." (değer yok).
  - Doğrulama: `validation/Isbns` (normalize: tire/boşluk sil, x → X; ISBN-10/13 checksum; yalnızca ASCII rakam) + `@Isbn`; `@HttpUrl`
    (mutlak http/https + host). ikisi de null/"" geçerli. ISBN tekliği yalnızca DB'de (`uk_books_isbn` → ISBN_ALREADY_EXISTS; ön kontrol YOK).
    Fiyat `@DecimalMin(0) @Digits(10,2)` → serviste `setScale(2)`. description ≤ 10.000 karakter (spec'e EK; TEXT 64 KB).
    authorIds/categoryIds ≤ 20, olmayan id → 400 alanlı (id söylenmez). Create'te boş opsiyonel metin = null; PATCH'te "" = temizle.
    PATCH'te status/stok alanları Jackson tarafından sessizce yok sayılır.
  - Yeni: `ErrorCode.BOOK_NOT_PUBLISHABLE`, `STOCK_BELOW_RESERVED` (409, INFO); `BookStatus.value()`/`fromParameter`; `WebConfig`
    BookStatus converter'ı; `Book.getAvailableQuantity()`; DTO'lar Create/UpdateBookRequest, StockAdjustmentRequest, AdminBookListRequest,
    AdminBookResponse, AdminBookSummaryResponse; `mapper/AdminBookMapper`.
  - Uyarlanan eski testler: ErrorProbeController overbooked (`initialStock = -1` → aynı CHECK 409), CatalogRepositoryTest (stok 10 ctor ile;
    CHECK testi `-1` ile, aynı `ck_books_reserved_le_stock` beklentisi), BookControllerTest (stok ctor ile, rezerv JDBC UPDATE ile).
  - Test altyapısı: `support/SqlCapture` (Hibernate `StatementInspector`, application-test.yml `session_factory.statement_inspector`,
    yalnızca start/stop arasında kaydeder); `ApiTestSupport` artık `outbox`'ı da temizler. Yeni testler: AdminBookControllerTest (20),
    BookStockColumnsTest (2), IsbnsTest (15), HttpUrlValidatorTest (12).
  - Uçtan uca (local seed + compose user-service, ADMIN token): a–g geçti. Yerel catalog_db'de 2 arşivlenmiş e2e kitabı ve 6 outbox
    satırı (published_at NULL) kaldı; seed published sayısı 11.

- catalog-service outbox relay (194 test yeşil; henüz commit edilmedi; user-service'e dokunulmadı, ortak modül yok):
  - user-service worker'ının birebir kopyası (`com.kitapsepeti.catalog`): `outbox/OutboxRelay` (DB kesintisinde tek satır WARN dahil),
    `OutboxPublisher`, `OutboxProperties`, `OutboxPublishException`, `EventRoutingKeys` (`BookUpserted → book.upserted`,
    `BookRemoved → book.removed`; bilinmeyen tip → IllegalStateException → WARN + tur durur = kuyruk bloklanır, user-service ile aynı),
    `config/RabbitConfig` (yalnızca exchange; kuyruk yok), `config/SchedulingConfig`, `config/ClockConfig` (catalog'da Clock bean'i yoktu).
  - Exchange tanımı user-service ile AYNI: `new TopicExchange(props.exchange(), true, false)` — `kitapsepeti.events`, topic, durable,
    autoDelete false, argümansız. Fark broker'da PRECONDITION_FAILED → `EventsExchangeCompatibilityTest` (önce user-service tanımıyla
    declare + Catalog declare hatasız; negatif kontrol: durable=false → PRECONDITION_FAILED).
  - Mesaj: messageId = outbox id, type = event_type, application/json + UTF-8, timestamp = created_at, PERSISTENT, header
    `aggregateType`/`aggregateId`. `application.yml` `spring.rabbitmq.*` + `app.outbox.*` user-service ile aynı anahtar/değerler.
  - `CreateBookRequest.initialStock` `@Max(1_000_000)`.
  - Test altyapısı: RabbitMQ konteyneri ayrı `RabbitTestcontainersConfiguration`'da (ApiTestSupport, CatalogServiceApplicationTests,
    JwksFlowTest, JwksOutageTest import eder; JPA/JDBC dilim testleri etmez → her dilim context'inde boşuna broker açılmaz).
    application-test.yml: `app.outbox.enabled: false`, `spring.rabbitmq.username/password: test` (ServiceConnection ezer).
  - Testler: `OutboxRelayIT` (sözleşme özellikleri + payload, BookRemoved yalnızca `book.removed` kuyruğunda, publish→PATCH→archive sırası,
    hata batch'i durdurur), `OutboxRelayBrokerOutageIT` (GERÇEK kesinti: kendi RabbitMQ'su sabit host portunda; stop → her turda tek
    satır WARN `Send failed (AmqpConnectException)`, ERROR/stack trace yok; yeni konteyner aynı portta → satırlar sırayla yayınlanır),
    `OutboxDisabledTest` (relay/SchedulingConfig/scheduled processor/TaskScheduler bean'i yok), `EventsExchangeCompatibilityTest`,
    `OutboxRelayDatabaseFailureTest`, `OutboxRelayUnknownEventTypeTest`, `OutboxSkipLockedTest`, `EventRoutingKeysTest`,
    BookStockColumnsTest (+updated_at), AdminBookControllerTest (initialStock 1_000_001 → 400, 1_000_000 → 201).
  - Uçtan uca: açılışta 6 bekleyen satır ~0,2 sn'de yayınlandı; broker'da tek `kitapsepeti.events`, 2 bağlantı (user-service + catalog);
    seed kitap 403 stok 0 → +5 (inStock değişti) → yeni BookUpserted ~1 sn'de yayınlandı; yeni user-service kaydı UserRegistered
    yayınlandı. Yerel catalog_db'de kitap 403 stoğu artık 5 (seed'de 0); user_db'de 2 yeni e2e kullanıcısı.
  - Commit `c03c0de` (+ memory-bank `dca4f75`), push edildi.

- catalog-service internal stok uçları + servisler arası API anahtarı (225 test yeşil; commit `958a708` + memory-bank `8a56ed8`, push edildi;
  migration, user-service, docker-compose'a dokunulmadı; süre dolumu Adım 9'da eklendi):
  - Uçlar (`controller/internal/InternalStockController`, `/internal/stock/reservations`): POST (yeni 201 + Location, aynı küme 200,
    farklı küme 409 RESERVATION_MISMATCH), POST `/{orderId}/commit`, POST `/{orderId}/release`, GET `/{orderId}`. Sözleşme
    `docs/api/catalog-internal-stock.md`. Kimlik: `X-Internal-Api-Key` (kalıp systemPatterns.md "Servisler arası kimlik").
  - Yeni ErrorCode'lar (409/INFO): INSUFFICIENT_STOCK, BOOK_NOT_AVAILABLE (ikisi `bookIds` uzantısıyla, `StockUnavailableException`),
    RESERVATION_MISMATCH, RESERVATION_RELEASED, RESERVATION_COMMITTED. `DbConstraints.isViolated(ex, ad)`. `ProblemDetailResponses` public.
  - Kurallar ve kilit sırası: systemPatterns.md "Stok rezervasyonu". `ReserveStockRequest` (items 1–50, quantity 1–100; tekrar eden bookId
    serviste → 400 `items`), `ReservationResponse` (status küçük harf, items bookId sıralı, unitPrice metin).
  - ORDER SÖZLEŞME NOTU: `unitPrice` okunduğu anki fiyat; mevcut rezervasyon döndürülürken GÜNCEL fiyat okunur → fiyat anlık görüntüsü
    order-service'in işi. Süresi geçmiş `held` hâlâ onaylanabilir (temizlik işi yok). 500 = işlem geri alındı, tekrar güvenli.
    Rezervasyon idempotency anahtarı `orderId`; order-service yeniden denemede aynı kalemleri göndermeli.
  - `.env`: `ORDER_INTERNAL_API_KEY` + `CATALOG_INTERNAL_KEY_ORDER_SHA256` üretildi (değer hiçbir yere yazılmadı); `.env.example` boş + yorum.
  - Değişen eski test: `SecurityRulesTest.internalPathRejectsEvenAdminJwt` (403 → 401; ayrı zincir). `ApiTestSupport` artık `MutableClockConfiguration` import eder.
  - Yeni testler: InternalAuthTest (5), InternalStockControllerTest (14), InternalStockConcurrencyTest (4: 10 sipariş/3 kopya → 3×201 + 7×409;
    aynı sipariş 5 eşzamanlı → 1×201 + 4×200; ters sıra [A,B]/[B,A] 20 tur; commit+release+reserve aynı kitaplarda 20 tur), InternalApiKeysTest (5,
    ApplicationContextRunner), InternalApiKeyAuthenticationFilterTest (3).
  - Uçtan uca (local seed): yayında stoğu tam 1 olan seed kitap yoktu → kitap 410 (stok 2) admin ile −1, akış a–g, sonra +2 ile 2/0'a döndü.
    Yerel catalog_db'de 410 için 1 released + 1 committed rezervasyon satırı ve 4 yeni outbox satırı; user_db'de 2 yeni e2e kullanıcısı.
  - `docs/events/book-*.md`: "relay yok" notu kaldırıldı, rezervasyon tetikleyicileri eklendi.

- Adım 9 — rezervasyon süre dolumu + seed tutarlılığı (248 test yeşil = 225 + 23; user-service 83; commit `e18960a`, push edildi; migration,
  user-service, docker-compose, .env'ye dokunulmadı; yeni olay türü yok, commit'in süre davranışı aynı):
  - Ana kod: `service/ReservationExpiryJob` (yeni), `StockProperties` (+ iç `Expiry` record), `SchedulingConfig` (AnyNestedCondition),
    `StockReservationRepository` (+ `findExpiredHeldOrderIds` native, `lockByOrderIdAndStatusSkipLocked`, `countByOrderIdAndStatus`),
    `StockReservationTransactions` (+ `findExpiredOrderIds`, `releaseExpired(orderId, now)`; `release()` gövdesi ortak `releaseHeld`'e
    taşındı). application.yml `app.stock.expiry` (true/30s/100), application-test.yml `enabled: false`. Kalıp: systemPatterns.md.
  - Seed: sipariş 601 (402×2, 404×2) ve 602 (402×1, 406×1) `held`, `expires_at` 2099-12-31, id 501–504; ON DUPLICATE KEY UPDATE.
  - Testler: `ReservationExpiryJobTest` (12: süre dolumu + BookUpserted, tek INFO, dokunulmayanlar, tam TTL anında bırakmama,
    sonrasında commit 409 / release 200, görevden önce commit, kilit yarışı [ayrı thread TransactionTemplate + `FOR UPDATE` latch'le
    tutulur → atlanır, bırakılınca sonraki tur], kısmi kilit, SQL'de `skip locked`, 150 sipariş → 100 + 50 en eski önce, hata izolasyonu,
    commit + görev 20 tur `runConcurrently`), `ReservationExpiryJobFailureTest` (5, Mockito; DB yok → tek WARN tur biter),
    `ReservationExpiryDisabledTest` (4; context'te bean yok + ApplicationContextRunner koşulları), `DevSeedExpiryTest` (1),
    DevSeedMigrationTest (+1 test: yeniden çalıştırma released rezervasyonu ve rezervi birlikte geri getirir; değişmez kontrolü).
    `runConcurrently` InternalStockTestSupport'a taşındı; InternalStockConcurrencyTest'in 4 senaryosu sonunda değişmez kontrolü.
  - Uçtan uca (local, TTL 20 sn): seed yeniden uygulandı, değişmez 0 (öncesinde 402/404/406 tutarsızdı); EXPLAIN aday sorgusu
    `ix_stock_reservations_status_expires` range; 403 admin +1 → rezerve (inStock false) → ~15 sn sonra görev bıraktı (tek INFO,
    BookUpserted inStock=true yayınlandı) → commit 409 RESERVATION_RELEASED; normal TTL ile yeniden açılıp 403 −1 ile seed değerine (0)
    döndü, değişmez 0. Yerel catalog_db: 403 için 1 released rezervasyon + outbox satırları; user_db'de 2 yeni e2e kullanıcısı.

- Adım 10 — catalog-service OpenAPI (260 test yeşil = 248 + 12; user-service 83; henüz commit EDİLMEDİ; uç davranışı/yolu/alan adı/
  durum kodu değişmedi, yalnızca anotasyon + springdoc; user-service sözleşmesi/testi, migration, compose, .env'ye dokunulmadı):
  - `springdoc-openapi-starter-webmvc-ui` (sürüm kök POM'dan, 3.1.1). `application.yml` `springdoc.*` user-service ile aynı anahtarlar
    + farklar: `packages-to-scan: com.kitapsepeti.catalog.controller` (src/test'teki `/test/errors/**` ve `*/ping` deneme controller'ları
    component scan ile her test context'ine giriyor; paths-to-match tek başına `/api/books/ping`'i dışarıda bırakamaz) ve
    `paths-to-match: /api/**, /internal/**`. `app.version: "@project.version@"`. SecurityConfig permitAll'a `/v3/api-docs/**`,
    `/swagger-ui/**`, `/swagger-ui.html` eklendi; internal zinciri `securityMatcher("/internal/**")` olduğundan karışmıyor.
  - `config/OpenApiConfig`: güvenlik şemaları `bearerAuth` (HTTP bearer JWT) + `internalApiKey` (apiKey, header `X-Internal-Api-Key`);
    GLOBAL security YOK (user-service'ten farklı). Tek `OpenApiCustomizer accessRulesAndErrorResponses`: yol önekinden erişim
    (`/api/admin/` → bearerAuth, `/internal/` → internalApiKey, diğer → `security: []`), standart hatalar addIfAbsent (girdi varsa 400,
    admin 401 + `WWW-Authenticate: Bearer` / 403 / 503, internal 401 + `ApiKey realm="internal"`, `{` içeren yola 404, hepsine 500),
    tüm 4xx/5xx içerik = yalnızca `application/problem+json` (uca özel problem+json şeması korunur), tag'ler ada göre sıralanır
    (aksi halde controller tarama sırası). Şemalar `Problem` (code enum = `ErrorCode.values()`), `FieldError`,
    `StockUnavailableProblem` (allOf Problem + `bookIds` uuid dizisi; yalnızca reserve 409).
  - Controller'larda `@Tag` (Books, Categories, Admin – Books/Publishers/Authors/Categories, Internal – Stock; en-dash),
    `@Operation(operationId, summary)`, başarı `@ApiResponse` (201 + Location, 204, reserve 201 + 200), uca özel 409/404 açıklamaları.
    Sorgu nesnelerine `@ParameterObject` (yoksa tek `request` nesne parametresi çıkıyordu). İki nested `Item` record'u aynı şema adına
    çakışıyordu → `@Schema(name = "ReserveStockItem" / "ReservationItem")`. DTO'larda `@Schema`: sort/status küçük harf enum + default,
    page/size default, response status enum'ları, delta/version/items açıklamaları. Bean Validation kısıtları otomatik görünüyor.
  - Sözleşme `docs/api/catalog-service.openapi.json`: 19 path / 31 operasyon. `OpenApiContractTest` (user-service uyarlaması,
    ApiTestSupport'tan türer): fark → JSON Pointer + dosyadaki/üretilen değer (160 karakter), üretilen doküman
    `catalog-service/target/openapi/catalog-service.openapi.json`, güncelleme komutu
    `.\mvnw.cmd -pl catalog-service test "-Dtest=OpenApiContractTest" "-Dopenapi.contract.update=true"`.
    `OpenApiDocsTest` (11): operasyon listesi birebir, public security boş + global yok, tüm admin bearerAuth (24), tüm internal
    internalApiKey (4), reserve 409 → StockUnavailableProblem, code enum, tüm hata içerikleri problem+json, public kitap şemasında stok
    sayısı yok, kısıt/enum'lar, gerçek 404 yanıtı Problem şekline uyuyor (`type` alanı yanıtta YOK), docs + swagger-ui anonim 200.
  - Drift kanıtı: MoveCategoryRequest'e geçici alan → test kırıldı (`/components/schemas/MoveCategoryRequest/properties/tempDriftProbe
    (dosyada yok)`), geri alındı → yeşil.
  - Uçtan uca (local): swagger-ui + api-docs anonim 200; public GET 200; ADMIN token ile /api/admin/books 200; token'sız 401
    problem+json `WWW-Authenticate: Bearer`; anahtarla rastgele orderId 404 RESOURCE_NOT_FOUND; anahtarsız 401 `ApiKey realm="internal"`.
    Canlı `/v3/api-docs` = sözleşme dosyası (servers `/`). user_db'de 2 yeni e2e kullanıcısı.

## Sonraki adımlar
- order-service (rezervasyon istemcisi; fiyat anlık görüntüsü kendisinde; `RESERVATION_RELEASED` → ödeme iadesi telafisi; süre dolumu
  olayı yok, GET ile sorgulanır; istemci `docs/api/catalog-service.openapi.json`'dan). catalog: actuator, Dockerfile + compose servisi
  (`USER_SERVICE_JWKS_URI`, `RABBITMQ_HOST` ve `CATALOG_INTERNAL_KEY_ORDER_SHA256` compose'ta).
- Backlog: yayınevi/yazar/kategori yeniden adlandırılınca yayındaki kitaplar için olay ÜRETİLMİYOR; Search servisi gelince
  yeniden indeksleme (ya da bu değişikliklerde etkilenen kitaplar için BookUpserted) gerekecek.
- Docker adımının commit'i (kullanıcı isteyince), logout, e-posta/parola değiştirme, consumer servisler, CORS.
- Açık konular: DataSourceHealthIndicator stack trace gürültüsü; CI pipeline yok (drift testi yalnızca yerel `mvnw test`'te).
- Açık konu (user-service VE catalog-service): outbox'ta yayınlanmış satırların temizliği (retention) yok; bilinmeyen event_type
  kuyruğun başını tıkar (her turda WARN, sonraki satırlar bekler).
- Açık konu (catalog): tıkanan süre dolumu siparişleri kuyruğun başını tıkayabilir (batch dolarsa), outbox'taki tanınmayan
  event_type sorunuyla birlikte çözülecek.
- Açık konu (catalog OpenAPI): `/v3/api-docs` her profilde açık (user-service ile aynı) → internal uçların şekli de herkese görünür
  (sır yok); prod'da `SPRINGDOC_ENABLED=false` düşünülmeli. Response şemalarında `required` yok (alanlar her zaman dolu olsa da).
- Yeni servisler eklendikçe kök POM kontrol listesini uygula (bkz. systemPatterns.md).
