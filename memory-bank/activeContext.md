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
    serviste → 400 `items`), `ReservationResponse` (status küçük harf, items bookId sıralı, unitPrice Adım 10b'den beri sayı).
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

- Adım 10 — catalog-service OpenAPI (260 test yeşil = 248 + 12; user-service 83; commit `5c30ca5` + memory-bank `725144f`, push edildi; uç davranışı/yolu/alan adı/
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

- Adım 10b — unitPrice sayı + response şemalarında required (262 test yeşil = 260 + 2; user-service 83; commit `3c40d4d`, push edildi;
  alan adı/yol/durum kodu/global Jackson, migration, compose, .env, user-service değişmedi):
  - `ReservationResponse.Item.unitPrice` String → `BigDecimal`; `StockReservationTransactions` `priceAmount.setScale(2, UNNECESSARY)`
    (artık `toPlainString()` yok). Özel serializer/anotasyon yok: Jackson varsayılanı BigDecimal'i `toString()` ile sayı yazar →
    `priceAmount` ile aynı biçim (129.90 → 129.90, 130 → 130.00). Replay ve GET aynı yoldan geçer.
  - Tüm response DTO'larında (public, admin, internal, PageResponse, kategori ağacı, *Ref) her zaman dolu alanlara
    `@Schema(requiredMode = REQUIRED)` (static import). Opsiyonel = V1'de NULL olabilen kolonlar: kitap isbn, description, pageCount,
    coverUrl, publishedAt (public detayda da; PUBLISHED satırda DB NULL'a izin veriyor) ve kategori parentId. Nullable işareti yok
    (user-service gibi; opsiyonel alan yalnızca `required` dışında). Request DTO'ları kontrol edildi, değişiklik gerekmedi.
  - Sözleşme yeniden üretildi: yalnızca 18 response şemasına `required` listeleri + ReservationItem.unitPrice `string` → `number`
    (+ açıklama). `docs/api/catalog-internal-stock.md` örneği `"unitPrice": 149.90` ve tablo satırı (BigDecimal ile okunmalı).
  - Yeni testler: `ReservationUnitPriceTest` (201 / replay 200 / GET: `isNumber()` ve ham token = public detaydaki `priceAmount`
    tokenı; 149.90, 130.00, 89.50), `OpenApiRequiredFieldsTest` (gerçek yanıtlar — public liste/detay/ağaç, admin kitap/yayınevi/yazar/
    kategori liste+detay, rezervasyon 201+GET — `/v3/api-docs` şemasına karşı özyinelemeli: required alan var ve null değil; 2xx'ten
    ulaşılan tüm şemalar kontrol edilmiş olmalı). Mutasyon kanıtı: `BookSummaryResponse.coverUrl` geçici REQUIRED → test kırıldı
    (`GET /api/books <PageResponseBookSummaryResponse>.items[0] <BookSummaryResponse>.coverUrl`), geri alındı.
    `OpenApiDocsTest`: unitPrice tipi = priceAmount tipi = number. `InternalStockControllerTest` unitPrice beklentileri sayı.
  - Kapsam dışı bırakıldı: `BookUpserted` olayındaki `priceAmount` hâlâ metin (docs/events/book-upserted.md).

- Adım 11 — catalog Docker + compose; CATALOG-SERVICE TAMAMLANDI (270 test yeşil = 262 + 8; user-service 83; commit `b128320` (A doküman) + `9443b82` (B), push edildi;
  migration, .env, .env.example, user-service davranışı değişmedi):
  - A (BookUpserted priceAmount sayıya) İPTAL, kullanıcı kararı: outbox `payload` kolonu MySQL `JSON`; MySQL kesirli sayıyı DOUBLE
    saklar (`CAST('{"a":149.90}' AS JSON)` → `149.9`, `130.00` → `130.0`, JSON_TYPE DOUBLE) → mesajda HTTP ile birebir biçim
    migration'sız mümkün değil. Metin kaldı; `docs/events/book-upserted.md`'ye neden paragrafı, `BookUpsertedEvent` javadoc'una sebep.
  - Actuator (yoktu, compose healthcheck için eklendi): pom `spring-boot-starter-actuator`, application.yml `management.*` user-service ile
    birebir (yalnızca health, show-details never, probes, liveness = livenessState, readiness = readinessState + db; discovery kapalı),
    SecurityConfig permitAll `GET /actuator/health`, `/actuator/health/**`. `config/ActuatorHealthTest` (8, user-service uyarlaması;
    token'la diğer actuator uçları 404). springdoc `packages-to-scan` sayesinde sözleşme dosyası değişmedi.
  - `catalog-service/Dockerfile`: user-service ile aynı (temurin 21 jdk/jre, go-offline + cache mount, `-DskipTests`, layered extract,
    `app` 10001, aynı JAVA_TOOL_OPTIONS), EXPOSE 8082. İmaj 595 MB. İçerik kontrolü: .env, *.pem, secrets, src, pom, mvnw,
    application-test.yml yok; jar'da `application-local.yml` + `db/seed/R__dev_seed_catalog.sql` var (yalnızca local profilde kullanılır).
  - KIRIK BULUNDU + DÜZELTİLDİ: kök POM'a `catalog-service` modülü (cd5d670) eklendikten sonra user-service Dockerfile'ı da build
    olmuyordu ("Child module /workspace/catalog-service does not exist"; çalışan imaj 29 Eylül'den). İki Dockerfile da artık iki modülün
    pom'unu kopyalıyor. user-service yeniden build edildi: adımlar koştu, runtime katmanları byte-aynı → imaj kimliği değişmedi.
  - Compose `catalog-service`: `8082:8082` (user-service gibi 127.0.0.1'siz), env tek tek (`CATALOG_DB_HOST=mysql`, CATALOG_DB_USER/
    PASSWORD, RABBITMQ_* user-service ile aynı biçim, `USER_SERVICE_JWKS_URI=http://user-service:8081/.well-known/jwks.json`,
    `CATALOG_INTERNAL_KEY_ORDER_SHA256`), secret yok (user-service de DB şifresini env ile alıyor; secret yalnızca JWT anahtarları),
    profil yok (default → seed yok; volume'daki 2 R__ satırı `repeatable:missing` ile yok sayılıyor, "validated 3 migrations"),
    depends_on mysql + rabbitmq `service_healthy`, user-service yok, healthcheck curl readiness, mem_limit 768m.
  - `.dockerignore` ve `.env.example` değişmedi (gerekenler zaten vardı). `docs/docker.md` yeni (catalog env'leri, Swagger, health).
  - Uçtan uca (compose): public 200, swagger 200, ADMIN (Docker user-service token'ı; JWKS compose ağından) 200, internal 404/401,
    410 rezerve→release stok 2/0→2/1→2/0 + değişmez 0, 403 stok 0→1→0 iki BookUpserted (published_at dolu, priceAmount STRING "89.90").
    RabbitMQ stop: healthy, düzeltmeler 200, 2 satır bekledi; start → 0. user-service stop: public/readiness 200, admin önbellek
    süresince 200, ~5 dk sonra 503 AUTHENTICATION_UNAVAILABLE (cause=UnknownHostException); start → 200. restart: migration yok,
    veri yerinde. Loglar: ERROR/stack trace 0; beklenen WARN'lar (internal 401, broker yokken outbox retry, 503); sır/token/e-posta yok,
    ama Spring AMQP INFO bağlantı satırında RabbitMQ kullanıcı adı geçiyor (user-service'te de aynı) → backlog.
  - Yerel veri: user_db'de 1 yeni e2e (ADMIN) kullanıcısı; catalog_db'de 410 için 1 released rezervasyon + 403 için 4 BookUpserted.

- Cart Adım 0 — `common` modülü (A) + compose portları 127.0.0.1 (B); SAF YENİDEN DÜZENLEME (henüz commit edilmedi; A ve B ayrı
  commit'lenecek; davranış/HTTP/log metni/OpenAPI değişmedi, `git diff docs/api` boş; migration, .env, outbox'a dokunulmadı):
  - Testler: common 25 (yeni), user-service 83 (aynı), catalog-service 262 (270 − 8: InternalApiKeysTest 5 + filtre testi 3 common'a
    taşındı; common'da 5 + 5 = 2 yeni: yanlış anahtar, sabit zamanlı karşılaştırma `mockStatic(MessageDigest, CALLS_REAL_METHODS)`).
    Toplam 370 (önce 353).
  - Yapı ve tarif: systemPatterns.md "common modülü". Servislerde kalan: `UserErrorCode`/`CatalogErrorCode` + API_CODES (sözleşme sırası),
    `GlobalExceptionHandler` alt sınıfları, catalog `DbConstraintCodes` (eski DbConstraints; test `DbConstraintCodesTest`), SecurityConfig
    kuralları, user-service RsaKeyConfig, catalog JwtProperties/InternalSecurityConfig, outbox.
  - Sapmalar: (1) security handler + internal filtre/entry point logger adları `com.kitapsepeti.common.security(.internal).*` oldu
    (mesaj metni aynı; GlobalExceptionHandler logger adı korundu). (2) `isViolated` iki serviste normalize semantiğinde birleşti
    (son '.' sonrası, büyük/küçük harf duyarsız; gerçek kısıt adlarında fark yok). (3) `ResourceNotFoundException` iki serviste aynı
    olduğu için common'a taşındı. (4) `JwtRoleConvertersTest`: Security 7 `FACTOR_BEARER` yetkisini de ekliyor (mevcut davranış).
  - Dockerfile: `COPY --parents */pom.xml ./` + `COPY --parents common/src <servis>/src ./`. Değerlendirilen: modül başına açık satır
    (eski; her yeni modülde tüm Dockerfile'lar değişir), Dockerfile başına `.dockerignore` (`<Dockerfile>.dockerignore`; her servis
    için ayrı liste bakımı), `--parents` (seçildi). Cache denemesi: src değişince go-offline dahil 1–8. adımlar CACHED.
  - B: compose mysql/rabbitmq/user-service/catalog-service host portları `127.0.0.1:` (adminer zaten öyleydi); `docs/docker.md` paragraf.
    LAN IP'den 8081/8082/3306/5672/15672 kapalı, localhost açık. Port değişimi mysql/rabbitmq'yu yeniden oluşturdu, named volume'lar yerinde.
  - Uçtan uca (compose): /api/me 200, public 200, admin 200 / token'sız 401 problem+json `Bearer`, internal anahtarsız/yanlış 401
    `ApiKey realm="internal"`, anahtarla 404; user-service durdurulup catalog yeniden başlatılınca admin 503 AUTHENTICATION_UNAVAILABLE,
    geri gelince 200. Loglarda .env sırları/JWT/e-posta/özel anahtar/stack trace 0.

- Cart Adım 0 commit + push edildi (`dab2d0c` A, `7a33669` B, `3aefe4b` memory-bank).

- Cart Adım 1 — cart-service iskeleti + cart_db + V1 (henüz commit edilmedi; entity/repository/controller/kalıcı güvenlik/Feign client YOK;
  user-service, catalog-service, common, Dockerfile'lar değişmedi; compose'a cart-service kaydı YOK):
  - Spring Cloud KARARI: `2025.1.3` (Oakwood) eklendi. Kaynak spring.io/projects/spring-cloud tablosu: "2025.1.x aka Oakwood |
    4.0.x, 4.1.x (Starting with 2025.1.2)"; Maven Central'da 2025.1.3 GA (2026.0.0-M1 milestone → kullanılmadı). Kök POM
    `spring-cloud.version` + `spring-cloud-dependencies` BOM import (dependencyManagement). BOM user/catalog'un çözülmüş bağımlılıklarını
    DEĞİŞTİRMEDİ (dependency:list HEAD ↔ şimdi: 195/195, fark 0). cart-service: `spring-cloud-starter-openfeign` (openfeign 5.0.3,
    feign 13.6.1; geçişli bcprov + commons-fileupload), `@EnableFeignClients` (client yok; `FeignClientFactory` bean'i testte).
  - Modül: `cart-service` (port 8083), ana sınıf `com.kitapsepeti.cart.CartServiceApplication` (`exclude = UserDetailsServiceAutoConfiguration`),
    `config/CartConfig` + `service/CartProperties` (`app.cart.max-quantity-per-item` 10 [1–99], `max-lines` 50 [≥1], @Validated; henüz kullanılmıyor).
  - GEÇİCİ `config/SecurityConfig`: yalnızca GET `/actuator/health`, `/actuator/health/**` açık; diğer her şey 401 ProblemDetail
    (`WWW-Authenticate: Bearer`, common `ProblemDetailSecurityHandlers`). Resource server/JWT ayarı YOK (sonraki adımda değişecek).
  - application.yml catalog yapısı: `CART_DB_HOST/PORT/USER/PASSWORD`, `cart_db`, Hikari 5000 ms, validate, OSIV kapalı, hibernate jdbc UTC,
    Flyway, actuator yalnızca health (readiness = readinessState + db). Seed, rabbitmq, springdoc, outbox YOK.
  - DB: `infra/mysql/init/20-cart-db.sh` (10-catalog kalıbı), compose mysql env `CART_DB_USER/PASSWORD`, `.env.example` yer tutucular.
    Kullanıcı `.env`'ye CART_DB_* ekledi → `docker compose up -d mysql` (yeniden oluşturuldu, volume korundu) → script elle çalıştırıldı.
    `SHOW GRANTS`: yalnızca `USAGE ON *.*` + `ALL ON cart_db.*`; `SHOW DATABASES`: cart_db, information_schema, performance_schema.
    user-service/catalog-service container'ları mysql yeniden oluşturulduktan sonra healthy kaldı.
  - V1 `V1__create_cart_tables.sql`: carts (status CHECK, `active_user_id` VIRTUAL generated + `uk_carts_active_user`, `ix_carts_user`),
    cart_items (FK CASCADE, quantity 1–99, fiyat ≥ 0, snapshot kolonları, `uk_cart_items_cart_book`). Outbox yok.
  - Collation DÜZELTİLDİ (V1 gerçek DB'ye uygulanmadan önce): `status VARCHAR(16) COLLATE utf8mb4_bin` → `ck_carts_status` ve
    `active_user_id` ifadesi büyük/küçük harfe duyarlı ('ACTIVE'/'Active' → 3819). Generated ifade metni değişmedi. Kural systemPatterns'te;
    catalog/user `_ai_ci` durum kolonları bilinen sorunlarda (V2 yok).
  - Yerel çalıştırma: `.\mvnw.cmd -pl cart-service spring-boot:run` → Flyway "Successfully applied 1 migration … v1", gerçek DB'de
    `carts.status` = utf8mb4_bin, `/actuator/health` UP, `/api/cart` 401, "generated security password" yok. NOT: `-pl cart-service`
    tek başına çalışınca common'ı `~/.m2`'de arar → önce `.\mvnw.cmd -pl common -am install -DskipTests` (ya da `-am` ile derle).
  - commons-fileupload 1.6.0: spring-cloud-starter-openfeign 5.0.3 → spring-cloud-openfeign-core 5.0.3 → feign-form-spring 13.6.1.
  - Testler (cart-service 42): CartSchemaConstraintsTest 25 (@JdbcTest), ActuatorHealthTest 10, CartServiceApplicationTests 3, CartPropertiesTest 4.
    Toplam: common 25, user 83, catalog 262, cart 42. `docker compose build user-service catalog-service` Dockerfile değişmeden başarılı.

- Cart Adım 1 commit + push edildi (`2b31367` kod, `45b432c` memory-bank).

- Cart Adım 2 — entity + repository (henüz commit edilmedi; servis/controller/güvenlik/Feign YOK; migration, user/catalog/common değişmedi):
  - `entity/CartStatus` (ACTIVE, CHECKED_OUT, ABANDONED) + `CartStatusConverter` (küçük harf yaz; okumada BİREBİR eşleşme, bilinmeyen
    değer → `IllegalArgumentException("Unknown cart status in database: '<v>'; expected one of [active, checked_out, abandoned]")`;
    catalog converter'ı `toUpperCase` ile okuduğu için 'ACTIVE'i kabul ederdi, cart etmez).
  - `entity/Cart` (aggregate root): UUIDv7 id, userId, status (varsayılan ACTIVE), createdAt/updatedAt (Instant). `active_user_id`
    EŞLENMEDİ (validate takılmadı). `items` = `@OneToMany(mappedBy="cart", cascade=ALL, orphanRemoval=true)` + `@OrderBy("addedAt ASC, id ASC")`,
    `List`, getter salt okunur görünüm. Fabrika `openFor(userId, clock)`; `addItem(bookId, qty, price, currency, title, coverUrl, clock)`
    (aynı kitap → ISE), `findItem(bookId)`, `findItemById(itemId)`, `removeItem(itemId)` (bool), `clear()`, `checkout()`/`abandon()`
    (yalnızca ACTIVE'den, aksi ISE). EK: geçmiş sepette add/remove/clear/changeQuantity/refreshSnapshot → ISE (`requireActive`). Setter yok.
  - `entity/CartItem`: UUIDv7 id, cart (LAZY, optional=false), bookId, quantity (1–99, aksi IAE), unitPriceSnapshot (scale 2,
    `RoundingMode.UNNECESSARY`; fazla ondalık/negatif → IAE), currencySnapshot (null → "TRY"), titleSnapshot, coverUrlSnapshot (null olabilir),
    addedAt, updatedAt. `changeQuantity`, `refreshSnapshot(price, currency, title, coverUrl)`. Constructor package-private.
  - Zaman: oluşturma anı `Clock`'tan (MICROS'a kesilir; INSERT'e açıkça yazılır). Güncelleme anı `@PreUpdate` → `Instant.now()` (UTC).
    `@CurrentTimestamp(event = UPDATE)` DENENDİ ve bırakıldı: Hibernate 7.4.5 kolonu INSERT'ten çıkarıyor → DB default'u (şimdi) yazılıyor,
    bellekteki saat değeriyle tutarsız. `@CreationTimestamp/@UpdateTimestamp` (catalog) Clock'u kullanmadığı için seçilmedi.
  - `repository/CartRepository`: `findByUserIdAndStatus` (`@EntityGraph("items")` → tek SQL, left join + `order by added_at, id`);
    `findActiveByUserIdForUpdate(userId)` (default metot → `lockByUserIdAndStatus` JPQL + PESSIMISTIC_WRITE → `... for update of c1_0`,
    satırlar yüklenmez). `existsByUserIdAndStatus` ve `CartItemRepository` YAZILMADI (gerek yok; satırlar aggregate üzerinden).
  - Kilit zaman aşımı: `jakarta.persistence.lock.timeout` ipucu (5000) MySQL'de ETKİSİZ çıktı (Hibernate 7.4.5 `MySQLLockingSupport`
    pozitif süreyi SQL'e yazamıyor, bağlantıya da uygulamıyor; test 5 sn yerine 30 sn bekledi) → kaldırıldı. Yerine Hikari
    `connection-init-sql: SET SESSION innodb_lock_wait_timeout = 5` (tüm cart bağlantıları; GLOBAL 50 aynen). Test: 5 sn sonra
    `PessimisticLockingFailureException`.
  - `default_batch_fetch_size: 50` application.yml'e (catalog gibi).
  - Flush sırası BULGULARI (Adım 6'da çözülecek): (1) aynı flush'ta `removeItem` + aynı kitabı `addItem` → INSERT önce çalışıyor →
    `uk_cart_items_cart_book` (orphanRemoval de DELETE'i sona bırakıyor); arada `flush()` → çalışıyor. (2) aynı flush'ta `checkout()` +
    aynı kullanıcıya yeni sepet → INSERT, UPDATE'ten önce → `uk_carts_active_user`; checkout'tan sonra `flush()` → çalışıyor.
  - Testler (cart-service 90): CartRepositoryTest 18 (@DataJpaTest), CartLockingTest 3 (iki thread + latch, NOT_SUPPORTED, ~17 sn),
    CartTest 18 (birim), CartStatusConverterTest 9 (birim), + mevcut 42. Test profili: `generate_statistics`, `support/SqlCapture`
    (catalog'dan kopya). Repository testleri `@ActiveProfiles("test")` (statistics/inspector için).

- Cart Adım 2 commit + push edildi (`333d19a` kod, `376ab3a` memory-bank).

- Cart Adım 3 — kalıcı güvenlik (Resource Server + JWKS) + Cart hata kodları (henüz commit edilmedi; controller/servis/Feign/internal/
  OpenAPI YOK; common, user-service, catalog-service, migration, .gitignore değişmedi):
  - Ana kod: application.yml (`jwk-set-uri` + `app.jwt.issuer`), `security/` JwtProperties, JwtSubjects, CurrentUserId,
    CurrentUserIdArgumentResolver, InvalidSubjectException; `config/` JwtDecoderConfig (common rs256 + sub UUID kontrolü), SecurityConfig
    (geçici olanın yerine), ClockConfig, WebConfig; `exception/` CartErrorCode (+ API_CODES), CartLimitExceededException,
    BookNotAvailableException, CatalogUnavailableException, GlobalExceptionHandler, DbConstraintCodes. Kurallar: systemPatterns "Güvenlik (cart-service)".
  - Test anahtarı: RSA çifti test JVM'inde üretilir (`support/TestJwt`), JWKS JDK HttpServer stub'ından (`support/JwksServer`);
    pem dosyası/.gitignore istisnası YOK.
  - Testler (cart 150 = 90 + 60): SecurityRulesTest 23, JwksOutageTest 3 (ayrı context), GlobalExceptionHandlerTest 7, CartErrorCodeTest 4,
    DbConstraintCodesTest 7, CurrentUserIdArgumentResolverTest 6, JwtSubjectsTest 8, CartServiceApplicationTests 5 (+2: JwtEncoder yok,
    Clock). Test-only controller'lar: `security/SecurityProbeController` (`/api/cart/_whoami`, `/api/other/ping`),
    `exception/ErrorProbeController` (`/api/cart/_errors/**`).
  - Değişen eski testler: CartServiceApplicationTests ve ActuatorHealthTest artık `ApiTestSupport`'tan türer (aynı context; annotation'lar
    tabana taşındı); ActuatorHealthTest'te yalnızca metot adı `everythingElseIsUnauthorized` → `everythingElseNeedsToken` (beklentiler aynı).
  - Root `clean verify`: common 25, user 83, catalog 262, cart 150.
  - Uçtan uca (Docker user-service gerçek USER token'ı, token yazdırılmadı): a) token'sız 401 `Bearer`; b) token'lı `/api/cart` 404
    NOT_FOUND (henüz controller yok); c) bozuk imza / çöp token 401 `Bearer error="invalid_token"`; d) user-service stop + cart yeniden
    başlatma (cart user-service olmadan açıldı) → 503 AUTHENTICATION_UNAVAILABLE + tek WARN (cause=ConnectException), token'sız 401,
    readiness 200; start → aynı token 404. Loglarda token/Bearer/e-posta yok; .env değerlerinden yalnızca RabbitMQ kullanıcı adı
    ("kitapsepeti" alt dizesi, zararsız). user_db'de 1 yeni e2e kullanıcısı.

- Cart Adım 3 commit + push edildi (`2119d3f` kod, `76e8a46` memory-bank).

- Cart Adım 4 — catalog-service'e toplu okuma ucu (henüz commit edilmedi; cart-service/user-service/common/migration değişmedi):
  - `GET /api/books/lookup?ids=...` (public; mevcut `GET /api/books/**` kuralı + `ignoringGet` kapsıyor, bozuk/süresi dolmuş
    Authorization yok sayılır). Yanıt `BookLookupResponse { items: BookSummaryResponse[] }` (`items` required): yalnızca yayındakiler;
    bulunamayan/taslak/arşiv sessizce yok; tekrarlı id bir kez; sıra = istekteki ilk geçiş. Stok adedi YOK (yalnızca `inStock`).
    cart bunu `GET /api/cart`'ta fiyat/stok tazelemek için kullanacak (Feign client sonraki adımda).
  - Parametre: `BookLookupRequest` (`@ModelAttribute` + `@ParameterObject`, BookSearchRequest kalıbı); `MAX_IDS = 50` tek yerde
    (limit tekrarları da sayar). Virgüllü (`ids=a,b`) ve tekrarlı (`ids=a&ids=b`) Spring varsayılanıyla ikisi de çalışır.
    Eksik / `ids=` / 51 id / bozuk UUID / boş eleman (`a,,b`) → hepsi 400 VALIDATION_FAILED, `errors[].field` = `ids…`;
    gönderilen değerler yanıtta yok (bağlama hatası mesajı "invalid value", limit mesajı "size must be between 1 and 50").
    `/lookup` literal yolu `/{id}`'den önce eşleşir (MALFORMED_REQUEST değil).
  - Sorgu: `BookRepository.findByIdInAndStatus` (`@EntityGraph("publisher")`) → `books join publishers where id in (...) and status=?`
    + yazarlar `default_batch_fetch_size` ile tek toplu sorgu. 1 id ve 50 id için 2 SQL (testle sabitlendi). İndeks/migration gerekmedi (PK).
  - OpenAPI: Books tag, `security: []`, 400/500 customizer'dan; `ids` şeması `minItems 1 / maxItems 50`. Sözleşme diff'i yalnızca
    yeni path + `BookLookupResponse` şeması. OpenApiDocsTest (operasyon listesi + public döngüsü) ve OpenApiRequiredFieldsTest güncellendi.
  - Testler: `BookLookupControllerTest` 14 (catalog 276). Root `clean verify`: common 25, user 83, catalog 276, cart 150.
  - Docker (seed id'leri): 2 yayında + 1 taslak → 2 item; 51 id → 400; token'sız ve bozuk Bearer → 200.

- Cart Adım 4 commit + push edildi (`c0e1c19` kod, `0455964` memory-bank).

- Cart Adım 5 — Catalog Feign client + gateway (henüz commit edilmedi; servis/controller YOK; catalog/user/common/kök pom değişmedi):
  - `client/CatalogClient` (`@FeignClient(name = "catalog", url = "${app.catalog.base-url}")`): `getBook(UUID)` → GET `/api/books/{id}`,
    `lookup(List<UUID>)` → GET `/api/books/lookup?ids=a&ids=b` (Feign varsayılanı tekrarlı parametre; Catalog kabul ediyor).
  - DTO'lar `client/CatalogBook(id, title, priceAmount, currency, coverUrl, inStock)`, `CatalogBookLookup(items)`: tolerant reader,
    zorunlular `@JsonProperty(required = true)` + compact ctor null kontrolü.
  - `client/CatalogGateway`: `requireAvailableBook(UUID)` (404 / inStock=false → BookNotAvailableException; 5xx, timeout, bağlantı,
    okunamayan/başka id → CatalogUnavailableException; diğer 4xx → IllegalStateException, cause'suz) ve `lookup(Collection<UUID>)`
    → `Map<UUID, CatalogBook>` (boş → çağrı yok; tekrarlar bir kez gönderilir; >`MAX_LOOKUP_IDS` (50) tekil id → IAE; istenmeyen id'ler
    atılır; 404 dahil 4xx → ISE). `InvalidCatalogResponseException` (paket içi) 2xx-ama-yanlış yanıtın nedeni.
  - application.yml: `app.catalog.base-url: ${CATALOG_BASE_URL:http://localhost:8082}`; `spring.cloud.openfeign.client.config.catalog`
    connect 1000 / read 2000 ms, `logger-level: none`. Retry yok (varsayılan NEVER_RETRY, testle).
  - Testler (cart 197 = 150 + 47; 2'si skipped live): CatalogGatewayTest 39, CatalogContractTest 5, CatalogConnectionRefusedTest 1 (ayrı
    bağlam), CatalogLiveTest 2 (`-Dcatalog.live=true` ile; Docker catalog'a karşı geçti: 401 → 145.00 TRY inStock; 401+412(draft)+402
    → 2 kalem). Read timeout ~2.01 sn, bağlantı reddi ConnectException ~0.1 sn. `ApiTestSupport` artık `CatalogStub` da açıyor.
  - Root `clean verify`: common 25, user 83, catalog 276, cart 197.
  - Not: `app.cart.max-lines` (50) ≤ `CatalogGateway.MAX_LOOKUP_IDS` şartı Adım 6'da `CartProperties` `@Max`'ına bağlandı.

- Cart Adım 5 commit + push edildi (`66b3d01` kod, `99a04fa` memory-bank).

- Cart Adım 6 — `GET /api/cart` + `POST /api/cart/items` (henüz commit edilmedi; PATCH/DELETE, internal snapshot, OpenAPI, Docker YOK;
  catalog/user/common, migration değişmedi):
  - Zaman Clock'tan: `@PreUpdate`'ler kaldırıldı. Değiştiren her metot Clock alır ve satırın + sepetin `updatedAt`'ini yeniler
    (`Cart.touch(clock)`; `CartItem.changeQuantity/refreshSnapshot(…, clock)`, `Cart.addItem/removeItem/clear(…, clock)`).
    SAPMA (spec'te yoktu): `checkout(clock)`/`abandon(clock)` da Clock alır (yoksa durum geçişinde updatedAt eski kalırdı). Geçmiş
    sepet `touch` edilemez (ISE). Değişen eski testler: CartTest (tüm çağrılar + 3 yeni zaman testi), CartRepositoryTest (iki zaman
    testi yeniden yazıldı: satır değişince sepet updated_at da saatle güncellenir — eskiden "sepet UPDATE edilmez" bekleniyordu;
    checkout updated_at = saat; diğer çağrılar yalnızca imza).
  - `CartProperties`: `maxQuantityPerItem` `@Min(CartItem.MIN_QUANTITY) @Max(CartItem.MAX_QUANTITY)` (1–99),
    `maxLines` `@Min(1) @Max(CatalogGateway.MAX_LOOKUP_IDS)` (1–50). 51 / 100 → context açılmaz (CartPropertiesTest).
  - Kod: `service/CartService` (TX'siz), `service/CartTransactions` (`findActive` readOnly; `addItem` READ_COMMITTED),
    `service/CartViewAssembler`, `service/CartContents` (TX içinde kopyalanan, id'siz satırlar), `dto/request/AddCartItemRequest`
    (`@NotNull bookId`, `quantity` 1–99, null → 1), `dto/response/CartResponse` + `CartLineResponse` + `CatalogStatus`,
    `controller/CartController`. Kurallar systemPatterns "Sepet servisi (cart-service)".
  - Testler (cart 237 = 197 + 40): CartControllerTest 26, CartConcurrencyTest 4 (2 farklı kitap → 1 sepet/2 satır; 10 × aynı kitap →
    adet 10; 12 → 10×200 + 2×409 QUANTITY; 49 satır + 5 yeni kitap → 1×200 + 4×409 LINE; 3 tekrar koşuda kararlı), CartServiceTest 5
    (Mockito: yeniden deneme kuralları), CartTest +3, CartPropertiesTest +2. `support/FakeCatalog` (CatalogStub yanıtlayıcısı: harita =
    yayındaki kitaplar), `ApiTestSupport`'a `@MockitoSpyBean CartRepository carts` (tek context korunur; yarış testleri
    `findActiveByUserIdForUpdate`'i taklit eder, rakip sepet REQUIRES_NEW TransactionTemplate ile commit edilir).
  - Root `clean verify`: common 25, user 83, catalog 276, cart 237 (2 skipped).
  - Uçtan uca (cart spring-boot:run + Docker user/catalog, gerçek USER + ADMIN token, yazdırılmadı): boş GET (DB'de sepet yok) →
    401 ekle → tekrar ekle adet 2 → 404×3 (seed 402 yayında ama stokta değil → 409 BOOK_NOT_AVAILABLE; bu yüzden ikinci kitap 404)
    → GET VERIFIED 587.00 → 412 taslak 409 → 401 +9 → 409 limit 10 → adet 100 → 400 → catalog stop: GET 200 UNAVAILABLE (snapshot
    587.00), POST 503 (7 ms, ConnectException) → start: VERIFIED → admin PATCH 401 159.90: priceChanged true, subtotal 616.80 → geri
    145.00. cart_db: kullanıcıda 1 active sepet, birden fazla aktif sepetli kullanıcı 0. Log: yalnızca 2 beklenen WARN; token/e-posta/
    kitap id'si/fiyat yok. Yerel veri: user_db'de 2 yeni e2e kullanıcısı (biri ADMIN), cart_db'de 1 sepet (2 satır), catalog_db'de
    401 için 2 BookUpserted (fiyat 145.00'e döndü).
  - Not: doğrulama mesajları JVM dilinde (tr) geliyor (`'99' değerinden küçük yada eşit olmalı`); girilen değer yok (mevcut davranış).

- Cart Adım 6 commit + push edildi (`3a378d6` kod, `f95ad17` memory-bank).

- Cart Adım 7 — `PATCH /api/cart/items/{bookId}`, `DELETE /api/cart/items/{bookId}`, `DELETE /api/cart/items` (henüz commit edilmedi;
  internal snapshot, OpenAPI, Docker YOK; catalog/user/common, migration değişmedi):
  - Kod: `CartTransactions.changeQuantity/removeItem/clear` (READ_COMMITTED, aktif sepet `findActiveByUserIdForUpdate`),
    `CartService` aynı adlı 3 metot (TX → assembler), `dto/request/UpdateCartItemRequest` (`@NotNull @Min(1) @Max(99) Integer quantity`),
    `CartController` 3 uç (hepsi GET ile aynı `CartResponse`). Kurallar ve damgalama kararları systemPatterns "Sepet servisi (cart-service)".
  - YENİ: `exception/MaskedRequestPaths` — yoldaki kitap id'si hata yanıtının `instance`'ına ve `METHOD URI -> CODE` log satırına
    girmesin diye `/api/cart/items/<x>` → `/api/cart/items/:bookId` (süslü parantez değil: common `URI.create` ile instance üretir,
    `{}` geçersiz URI → instance boş kalırdı). Uygulandığı yerler: cart `GlobalExceptionHandler` (isteği alan tüm taban handler'ları
    override edilip maskeli istekle `super`'e; `handleExceptionInternal` WebRequest'i sarar), `CartViewAssembler` WARN'ı, `SecurityConfig`
    (common entry point / access denied / failure handler lambda ile sarılır → 401/403/503). Yalnızca yönlendirmeden sonra; dispatch etkilenmez.
    Diğer yollar aynen (ör. test probe `/api/cart/_catalog/books/<id>` logu değişmedi).
  - Testler (cart 264 = 237 + 27): `controller/CartItemChangesTest` 24 (20 metot, biri 5 parametreli), CartConcurrencyTest +3 (5 eşzamanlı
    PATCH 2..6 → hepsi 200, son değer kümeden; DELETE + PATCH 5 tur → DELETE 200, PATCH 200|404, satır yok; clear + POST 5 tur → satırlar
    boş ya da yalnızca yeni kitap, tek aktif sepet). 500 yok.
  - Root `clean verify`: common 25, user 83, catalog 276, cart 264 (2 skipped).
  - Uçtan uca (cart spring-boot:run + Docker user/catalog; yeni e2e kullanıcısı, Adım 6 kullanıcısının kimlik bilgisi yok → sepet aynı
    biçimde yeniden kuruldu: 401×2, 404×3): a) PATCH 401 → 5: 200 (snapshot 145.00, itemCount 8) b) PATCH 11 → 409 limit 10, instance
    `/api/cart/items/:bookId`, DB adedi 5 c) DELETE 404 → 200 (1 satır) d) tekrar → 200 e) DELETE tümü → boş, sepet active|0 f) 405 ekle →
    aynı sepet id'si; kullanıcıda 1/1 sepet, birden fazla aktif sepetli kullanıcı 0. Token'sız 401, bozuk UUID 400 MALFORMED_REQUEST.
    Log: token/sub/e-posta/parola/kitap id'si/`kitap-degil` yok; hata satırları maskeli. Yerel veri: user_db'de 1 yeni e2e kullanıcısı,
    cart_db'de 1 sepet (405×1).
  - `CartService.md` repoda YOK (aranan: `**/CartService*.md`, sepet/cart adlı tüm .md) → API tablosu tutarlılığı kontrol edilemedi.

- Cart Adım 7 commit + push edildi (`c692567` kod, `a3b7abe` memory-bank).

- Cart Adım 8 — `POST /internal/cart/snapshot` + internal güvenlik zinciri (commit `2135ac6` + `dd01c0c`, push edildi; CartCheckedOut/RabbitMQ, OpenAPI,
  Docker YOK; common/catalog/user, migration, .env değişmedi):
  - Ana kod: `config/InternalSecurityConfig` (@Order(1)), `config/InternalAuthConfig` (özet zorunlu), `SecurityConfig` `@Order(2)`,
    `controller/internal/InternalCartController`, `service/CartSnapshotService`, `dto/internal/CartSnapshotRequest|Response|Item`,
    application.yml `app.internal-auth`, `.env.example` `CART_INTERNAL_KEY_ORDER_SHA256`. Kurallar systemPatterns (cart güvenlik + sepet servisi).
  - Env adı `CART_INTERNAL_KEY_ORDER_SHA256` (catalog `CATALOG_INTERNAL_KEY_ORDER_SHA256` karşılığı), property
    `app.internal-auth.clients[0].key-sha256`, istemci adı `order-service`.
  - Testler (cart 287 = 264 + 23): `controller/internal/InternalCartSnapshotTest` 19, `config/InternalAuthConfigTest` 4. Eski testlerde
    yalnızca tam bağlam açan 4 kökte (`ApiTestSupport`, JwksOutageTest, CatalogLiveTest, CatalogConnectionRefusedTest) `InternalTestKeys.register`.
  - Root `clean verify`: common 25, user 83, catalog 276, cart 287 (2 skipped).
  - Uçtan uca (cart spring-boot:run, `.env`'deki özetle açıldı): anahtarsız / yanlış anahtar / kullanıcı Bearer → 401
    `ApiKey realm="internal"`; internal anahtarla `/api/cart` → 401 `Bearer`; loglarda anahtar/özet/userId/token/e-posta yok, internal
    satırlar yalnızca `Rejected internal request POST /internal/cart/snapshot -> UNAUTHORIZED`. AÇIK: `.env`'deki ORDER_INTERNAL_API_KEY
    ile snapshot da 401 → `.env`'deki CART_INTERNAL_KEY_ORDER_SHA256 bu anahtarın özeti değil (64 hex, uygulama açılıyor; OS ortam
    değişkeni yok). İki kez denendi, kullanıcı uçtan uca testi atlamayı seçti; 200 yolu yalnızca testlerde doğrulandı. Değeri
    `CATALOG_INTERNAL_KEY_ORDER_SHA256` ile aynı yapmak ya da yeniden üretmek gerekiyor. user_db'de 2 yeni e2e kullanıcısı.
    ÇÖZÜLDÜ (Adım 9 ön adımı): kullanıcı özeti yeniden üretti; yeni süreçte açılan cart'a gerçek anahtar + rastgele userId → 200
    `{"cartId":null,"updatedAt":null,"items":[]}`, logda anahtar yok.

- Cart Adım 9 — OpenAPI (commit `6fcd291` + memory-bank `bb2e0cf`, push edildi; Docker YOK; common/catalog/user, migration, .env değişmedi; uç davranışı, DTO alan
  adları, durum kodları değişmedi — yalnızca anotasyon):
  - `pom.xml` springdoc-openapi-starter-webmvc-ui (sürüm kök BOM'dan, 3.1.1). application.yml `springdoc` (catalog ile aynı:
    packages-to-scan `com.kitapsepeti.cart.controller`, paths `/api/**, /internal/**`, order-by-keys, `SPRINGDOC_ENABLED`) +
    `app.version: "@project.version@"`. `SecurityConfig` (kullanıcı zinciri) permitAll'a `/v3/api-docs/**`, `/swagger-ui/**`,
    `/swagger-ui.html`; internal zinciri yalnızca `/internal/**` eşlediği için docs'u etkilemiyor (testli).
  - `config/OpenApiConfig` (kurallar systemPatterns "cart-service OpenAPI"), controller'larda `@Tag`/`@Operation`/`@ApiResponse`,
    DTO'larda `@Schema` (required/nullable, 1–99, açıklamalar; para alanları `type: number`, format yok, açıklamada "2 ondalık basamak").
  - Internal uç kararı: catalog internal uçlarını OpenAPI'de (tag `Internal – Stock`) + ayrıca md'de belgeliyor → cart internal ucu da
    OpenAPI'de, tag `Internal`, `internalApiKey`. Ayrı md yazılmadı (docs/api/README yok).
  - Sözleşme `docs/api/cart-service.openapi.json`: 4 path, 6 operasyon; şemalar AddCartItemRequest, UpdateCartItemRequest,
    CartResponse, CartLineResponse, CatalogStatus, CartSnapshotRequest, CartSnapshotResponse, CartSnapshotItem, Problem,
    CartLimitProblem, FieldError. Problem.code enum = CartErrorCode.API_CODES sırası.
  - Testler (cart 301 = 287 + 14): `config/OpenApiContractTest` 1 (drift; bir alan bozulunca yol + değer + yeniden üretim komutuyla
    kırıldığı doğrulandı, geri alındı), `config/OpenApiDocsTest` 12 (anonim docs/swagger, internal başlığı docs'u etkilemez, tam 6
    operasyon, test-only/actuator path ve şema yok (probe uçları bağlamda var), tag sırası, path önekine göre güvenlik + 401 başlıkları,
    operasyon başına tam yanıt kodu kümesi, 409/503 kodları + `limit`, enum = API_CODES sırası, hatalar yalnızca problem+json,
    sınırlar/nullable/para, gerçek hata yanıtları (POST 409 limit 10, PATCH 404, DELETE bozuk UUID 400, POST 400, GET 401 Bearer,
    internal 401 ApiKey) dokümanla uyuşuyor), `config/OpenApiRequiredFieldsTest` 1 (boş sepet, dolu VERIFIED + satıştan kalkmış satır,
    Catalog kapalı UNAVAILABLE, karışık para birimi, dolu/boş/aktif-boş snapshot; coverUrl nullable kaldırılınca kırıldığı doğrulandı).
  - Root `clean verify`: common 25, user 83, catalog 276, cart 301 (2 skipped = CatalogLiveTest).
  - Yerel: `/v3/api-docs` 200 (4 path, 6 operasyon), `/swagger-ui/index.html` 200, `/swagger-ui.html` 302 → index; `/api/cart` token'sız 401.
  - Belgelenmeyen gerçek yollar (catalog ile aynı tercih): 406/415, ilk-sepet yarışında ikinci kısıt ihlali → 409 CONFLICT (pratikte
    olmaz), kilit zaman aşımı → 500 kapsamında.

- Cart Adım 10 — Docker + compose (commit `77ff615`, push edildi; catalog/user Dockerfile + compose kayıtları, common, migration, .env
  değişmedi). CART SERVİSİ TAMAMLANDI (Adım 0–10).
  - `cart-service/Dockerfile`: catalog'unkinin birebir kopyası (servis adı + `EXPOSE 8083`); 21-jdk build → 21-jre runtime, uid/gid
    10001 `app`, layered extract, Dockerfile HEALTHCHECK yok (compose'ta), ARG/ENV'de sır yok. İmaj 618 MB disk / 189 MB içerik.
  - Compose `cart-service`: `127.0.0.1:8083:8083`, `depends_on` yalnızca mysql (`service_healthy`; user/catalog'a bağımlılık YOK),
    env tek tek (env_file yok): CART_DB_HOST, CART_DB_PORT, CART_DB_USER `${:?}`, CART_DB_PASSWORD `${:?}`, USER_SERVICE_JWKS_URI
    (`http://user-service:8081/.well-known/jwks.json`), CATALOG_BASE_URL (`http://catalog-service:8082`),
    CART_INTERNAL_KEY_ORDER_SHA256 `${:?}`. restart `unless-stopped`, healthcheck curl readiness (10s/5s/5/40s), `mem_limit 768m`.
    Issuer env DEĞİL (catalog gibi yml'de sabit `kitapsepeti-user-service`) — spesifikasyondan sapma olarak raporlandı.
  - Health: application.yml'de Spring Cloud'un `refreshScope` + `discoveryComposite` katkıları kapatıldı; Feign health indicator
    eklemiyor. Katkılar tam olarak db, diskSpace, livenessState, readinessState, ping, ssl. Ayrıntı yok, yalnızca health expose.
  - Testler (cart 312 = 301 + 11): `ActuatorHealthTest` 20 (probe'lar katı `{"status":"UP"}`, kök health groups, diğer actuator yolları
    anonim 401 / ADMIN token'la 404, Catalog 503 dönerken üç health yolu UP ve Catalog'a istek yok, katkı listesi yalnızca yerel),
    `CatalogConnectionRefusedTest` +1 (Catalog adresinde kimse dinlemezken health + readiness UP).
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped = CatalogLiveTest). BUILD SUCCESS.
  - Docker uçtan uca (gerçek token, yazdırılmadı): GET boş → 2 kitap POST → PATCH → satır DELETE → GET 1 satır / 3 adet / 435.00 TRY
    VERIFIED. Internal snapshot gerçek anahtarla 200 (1 satır), anahtarsız 401. Catalog `stop`: cart healthy kaldı (failingStreak 0), GET
    200 UNAVAILABLE (snapshot fiyatları), POST 503 CATALOG_UNAVAILABLE 66 ms (sonrakiler 6–9 ms; durmuş container'ın adı çözülmüyor →
    UnknownHostException, connect timeout'a hiç gelinmiyor); `start` sonrası VERIFIED. `restart cart-service` → sepet aynen (2 satır /
    4 adet / 534.00). Restart öncesi alınan token restart sonrası 200 (JWKS yeniden çekildi, sonra önbellekten).
  - İmaj güvenliği: `docker history` sır deseni 0; Env adları PATH, JAVA_HOME, LANG, LANGUAGE, LC_ALL, JAVA_VERSION, JAVA_TOOL_OPTIONS;
    `/app` yalnızca BOOT-INF, META-INF, org. İmaj içinde `find` ile .env/*.pem araması auto-review'a takıldı (onay kartı açılamadı) →
    statik kanıt: runtime yalnızca extract katmanlarını kopyalar, build yalnızca mvnw/.mvn/pom'lar/common+cart src; `.dockerignore`
    .env, .env.*, secrets/, **/*.pem dışlar.
  - Log: token/Bearer/anahtar başlığı/64-hex/userId/kitap id'si/e-posta 0; WARN'lar yalnızca beklenenler (internal 401, Catalog
    UnknownHost ile GET UNAVAILABLE + POST 503). Loglardaki iki UUID Spring Cloud `GenericScope BeanFactory id` (rastgele, kullanıcı verisi değil).
  - Yerel veri: user_db'de 2 yeni e2e kullanıcısı (`e2e-cart10-…@example.test`; toplam e2e 24 / 46 kullanıcı); cart_db 4 sepet, 6 satır.
    Temizlik SQL'i önerildi, ÇALIŞTIRILMADI.
  - Not (araç): ajan Shell çağrıları arasında yalnızca ortam değişkenleri kalıcı; PowerShell değişken/fonksiyonları kaybolur.

- PAYMENT BAŞLADI (Faz 7, payment-service, port 8087). Planlama kararları:
  - v1 sağlayıcısı mock; ödeme oluşturulunca mock kendi webhook ucuna imzalı (HMAC) sonuç gönderir (sonraki adımlar).
  - Sipariş başına TEK ödeme (`uk_payments_order`).
  - Sonuç outbox + RabbitMQ ile Order'a gider.
  - Kullanıcıya açık uç yok: JWT / Resource Server YOK. Yalnızca internal (API key) ve webhook (HMAC imza). OpenFeign hiç gerekmiyor.
- Payment Adım 1 — iskelet + DB + V1 (commit `5c51261` + memory-bank `b577ad9`, push edildi; diğer servisler, common, migration'ları
  değişmedi; uç/entity/güvenlik/amqp yok):
  - Kök pom `<module>payment-service</module>`; artifactId `payment-service` (spesifikasyondaki `kitap-sepeti-payment-service` yerine:
    diğer servisler `<ad>-service`, yalnızca common `kitap-sepeti-` önekli; Dockerfile kalıbı `<svc>/target/<svc>-*.jar`). Test
    bağımlılıklarından `spring-boot-starter-security-test` çıkarıldı (security'yi test classpath'ine getirirdi).
  - `infra/mysql/init/30-payment-db.sh` (20-cart-db.sh birebir; LF, subshell `set -eu`, idempotent). Compose mysql env
    `PAYMENT_DB_USER/PASSWORD` `${VAR:?}` (CART_DB_* mysql'de `:?`'siz; istenen gibi). `.env.example` iki ad + "yalnızca harf/rakam".
  - Yerel: `.env` kaydedilmemişken compose tüm komutlarda durdu (`:?`); kullanıcı kaydetti → `docker compose up -d mysql` (yeniden
    oluşturuldu, volume korundu) + script elle bir kez (ikinci çalıştırma da sorunsuz). SHOW GRANTS: `USAGE ON *.*` + `ALL ON payment_db.*`;
    `cart_db` SELECT reddedildi (1142). user/catalog/cart mysql yeniden oluşunca readiness 200, healthy.
  - application.yml cart üslubu (8087, PAYMENT_DB_*, Hikari 5000 + lock wait 5, validate, OSIV kapalı, UTC, Flyway, yalnızca health,
    readiness = readinessState + db). Security/cloud/springdoc/app blokları yok.
  - V1: payments (uk_payments_order, uk_payments_provider_ref, ck_payments_provider/amount/currency/status/failure,
    ix_payments_status_created), provider_events (uk_provider_events_provider_event, fk_provider_events_payment RESTRICT,
    ck_provider_events_provider/event_type, ix_provider_events_payment = FK indeksi), outbox (catalog'la birebir; yalnızca yorum
    örnekleri 'Payment'/'PaymentSucceeded').
  - Testler (payment 60): `PaymentServiceApplicationTests` 11 (bağlam + Flyway V1, security/amqp/feign/springdoc classpath'te yok,
    health/liveness/readiness katı UP, diğer actuator 404), `schema/PaymentSchemaConstraintsTest` 49 (tablolar/engine/collation,
    kolonlar, binary collation, isimli kısıt/CHECK metinleri/FK RESTRICT/indeksler, kart kolonu yok, payments a–g, provider_events a–d,
    outbox DDL = catalog, outbox kolonları/default/geçersiz JSON).
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 60. `spring-boot:run` → health UP, V1 uygulandı,
    log'da WARN yok.
  - Commit mesajı notu: kullanıcı sonradan commit'leri "feat(cart): add Dockerfile and compose service with local-only health" /
    "feat(payment): add payment-service module with payment_db and V1 schema" / "docs: update memory bank for cart step 10 and payment
    step 1" olarak ayırmak istedi; içerik ayrımı zaten aynıydı (`77ff615`, `5c51261`, `b577ad9`) ama push edilmişti → yeniden adlandırma
    force push gerektirir (yasak), dokunulmadı.

- Payment Adım 2 — entity, repository, alan geçişleri, sağlayıcı soyutlaması, mock sonuç kuralı (henüz commit edilmedi; uç, güvenlik,
  amqp, outbox kodu, webhook, HTTP istemcisi YOK; migration, diğer modüller, common değişmedi):
  - Kod (`com.kitapsepeti.payment`): `entity/` DbEnum, StrictDbEnumConverter, PaymentStatus(+Converter), PaymentProviderType(+Converter),
    ProviderEventType(+Converter), TransitionResult, Payment, ProviderEvent; `repository/` PaymentRepository, ProviderEventRepository;
    `provider/` PaymentProvider, ProviderPayment, ProviderPaymentRequest, MockPaymentProvider, MockOutcome, MockOutcomeRule;
    `config/` PaymentProperties, PaymentConfig, ClockConfig; application.yml `app.payment` bloğu. Kurallar systemPatterns "Ödeme alanı".
  - Sapmalar: ID akışı "attach" (kullanıcı seçti); üç ayrı converter yerine ortak taban; MAX_AMOUNT kontrolü; `MockOutcomeRule` bean'i;
    "setter yok" testi derlemeyle sağlanıyor (setter yok, protected ctor); boş `app.payment.provider=` mock'a düşüyor (açılmayı durdurmuyor).
  - Bulgu: `provider_payment_id` (ve `provider_event_id`) kolonları `utf8mb4_0900_ai_ci` → `findByProviderTypeAndProviderPaymentId`
    büyük/küçük harf duyarsız eşler ("MOCK_FIND" = "mock_find", testte belgelendi) ve `uk_payments_provider_ref` /
    `uk_provider_events_provider_event` yalnızca harf büyüklüğü farklı iki referansı çakıştırır. Mock referansları küçük harf UUID →
    sorun yok; büyük/küçük harf duyarlı kimlikli gerçek sağlayıcıda (ör. Stripe) V2 ile `utf8mb4_bin` düşünülmeli.
  - Testler (payment 177 = 60 + 117): PaymentTest 44, DbEnumConvertersTest 24, MockOutcomeRuleTest 17, PaymentPropertiesTest 12,
    PaymentRepositoryTest 11 (@DataJpaTest + Testcontainers), ProviderEventTest 6, MockPaymentProviderTest 2,
    PaymentServiceApplicationTests 12 (+1: tek MOCK sağlayıcı bean'i, UTC Clock), PaymentSchemaConstraintsTest 49.
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 177. `spring-boot:run` → Hibernate validate geçti,
    health + readiness UP, log'da WARN/ERROR 0.
  - Commit `13bcd86` (kod) + `dbb3630` (memory-bank), push edildi.

- Payment Adım 3 — V2, güvenlik, internal ödeme oluşturma/sorgulama ucu (henüz commit edilmedi; webhook, outbox, amqp, mock dispatcher
  YOK; V1, diğer modüller ve common değişmedi):
  - V2 `V2__provider_ids_case_sensitive.sql`: `payments.provider_payment_id` VARCHAR(128) utf8mb4_bin NULL,
    `provider_events.provider_event_id` VARCHAR(128) utf8mb4_bin NOT NULL; UNIQUE'ler korundu (yerel DB'de doğrulandı). Adım 2 bulgusu kapandı.
  - Güvenlik (cart Adım 8 kalıbı): `spring-boot-starter-security`; `UserDetailsServiceAutoConfiguration` exclude.
    `InternalSecurityConfig` @Order(1) `/internal/**` (X-Internal-Api-Key, istemci `order-service`, env
    `PAYMENT_INTERNAL_KEY_ORDER_SHA256` → `app.internal-auth.clients[0].key-sha256`; yok/boş/bozuk → açılmaz, `InternalAuthConfig`).
    `SecurityConfig` @Order(2): GET health(/**) + `/error` permitAll, gerisi denyAll; anonim → 403 FORBIDDEN ProblemDetail,
    WWW-Authenticate YOK (entry point access denied handler'a delege eder; JWT yok → Bearer challenge yanlış olurdu).
  - Hata: `PaymentErrorCode` PAYMENT_ORDER_MISMATCH 409 (INFO), PAYMENT_PROVIDER_UNAVAILABLE 503 (WARN, logda yalnızca kök neden sınıf adı);
    `DbConstraintCodes` (3 UNIQUE → CONFLICT); `MaskedRequestPaths` `/internal/payments/<x>` → `/internal/payments/:paymentId`.
    Common filtresinin INFO/401 logu da maskeli: `InternalSecurityConfig.MaskedPathFilter` common filtresine maskeli isteği verir,
    zincirin geri kalanı orijinal istekle devam eder.
  - Uç: `POST /internal/payments` (orderId, userId, amount ≥0.01 Digits(10,2), currency ^[A-Z]{3}$) → 201 + Location / 200;
    `GET /internal/payments/{id}` → 200 / 404 RESOURCE_NOT_FOUND / 400. Yanıt `PaymentResponse` (userId yok, redirectUrl v1'de hep null).
  - Akış: `PaymentService` (TX yok) → `PaymentTransactions.findOrCreate` (READ_COMMITTED; var+eşleşir → döner, var+farklı → 409,
    yok → initiate+saveAndFlush; `uk_payments_order` ihlali → yeni TX'te bir kez tekrar) → initiated + referanssız ise sağlayıcı TX DIŞINDA
    (RuntimeException ya da geçersiz referans → 503, ödeme referanssız kalır; tekrar istek yeniden dener) → `attachReference`
    (`findByIdForUpdate` PESSIMISTIC_WRITE; boş → yaz, aynı → no-op, farklı → mevcut korunur + WARN değer yok; final+referanssız → dokunma).
    Final ödeme → sağlayıcı çağrılmaz, 200.
  - Testler (payment 220 = 177 − 1 + 44): InternalPaymentControllerTest 27, PaymentSchemaConstraintsTest 51 (+2), PaymentRepositoryTest 12
    (+1), SecurityRulesTest 8, InternalAuthConfigTest 4, PaymentConcurrencyTest 2, PaymentServiceApplicationTests 11 (−1 security classpath
    parametresi; diğer actuator 404 → 403). Eşzamanlılık: 10 paralel ilk istek → tek satır, tek 201, sağlayıcı bu koşularda 1 kez;
    deterministik attach yarışında ilk referans korunur. Test tabanı `ApiTestSupport` (`@MockitoSpyBean` PaymentProvider + PaymentRepository).
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 220. `spring-boot:run` → V2 uygulandı
    (flyway_schema_history 1, 2), health UP. Uçtan uca: anahtarsız 401 (ApiKey challenge), POST 201 → aynı 200 → farklı tutar 409 →
    GET 200, durum initiated, referans `mock_`; loglarda id/tutar/referans/anahtar/özet yok.

  - Commit `e5b4132` (kod) + `1dad3a0` (memory-bank), push edildi.

- Payment Adım 4 — ödeme sonucu servisi + outbox → RabbitMQ (commit `1f5bce0` + memory-bank `a98ad7a`, push edildi; webhook, provider_events yazımı, mock
  dispatcher, kurtarma görevi YOK; migration, diğer modüller ve common değişmedi):
  - Outbox kodu catalog'dan KOPYA (Order fazında common'a taşıma adayı; user-service ↔ catalog ↔ payment artık üç kopya):
    `entity/OutboxEvent`, `repository/OutboxRepository` (SKIP LOCKED batch), `outbox/` OutboxRelay, OutboxPublisher,
    OutboxProperties, EventRoutingKeys, OutboxPublishException; `config/RabbitConfig` (açılışta `AmqpAdmin.initialize()`),
    `config/SchedulingConfig` (yalnızca outbox koşulu; catalog'daki AnyNestedCondition gereksiz), `service/OutboxService`.
  - Exchange `kitapsepeti.events` (topic, durable, autoDelete değil — user/catalog ile ORTAK; kullanıcının örneği
    "kitapsepeti.payment" sistem kuralına aykırı olduğu için kullanılmadı). Routing key `payment.succeeded` / `payment.failed`.
    Mesaj: messageId = outbox id = payload `eventId`, type = event_type, contentType application/json, encoding UTF-8,
    timestamp = created_at, persistent, header aggregateType/aggregateId (catalog ile aynı).
  - Catalog'dan farklar: (1) OutboxEvent id'si INSERT'ten önce `UuidVersion7Strategy.INSTANCE.generateUuid(null)` ile üretilir
    (catalog'daki `@UuidGenerator(VERSION_7)` ile aynı üretici) çünkü payload'da `eventId` var; `OutboxService.append(..., Function<UUID,
    Object>)` payload'ı id'den kurar ve `EntityManager.persist` kullanır (atanmış id'de `save` → merge + SELECT olurdu).
    (2) `CachingConnectionFactory` logger'ı WARN (RabbitMQ kullanıcı adı INFO'da yazılmasın; catalog backlog'u).
  - Olaylar: `service/event/PaymentSucceededEvent(eventVersion, eventId, paymentId, orderId, amount, currency, occurredAt)`,
    `PaymentFailedEvent(+ failureCode)`; aggregate_type `payment`; amount metin (`setScale(2).toPlainString()`); occurredAt = geçişin
    updated_at'i; userId yok; Succeeded'da failureCode alanı YOK (olay tipine göre ayrı record, catalog BookRemoved gibi).
    `docs/events/payment-succeeded.md`, `payment-failed.md` (book-upserted biçimi).
  - `PaymentResults.recordSucceeded/recordFailed` (@Transactional READ_COMMITTED, REQUIRED): `findByIdForUpdate` (yoksa 404 exc.)
    → succeed/fail → APPLIED ise aynı TX'te outbox; ALREADY_IN_STATE/CONFLICTING_FINAL olay yok; CONFLICTING_FINAL → WARN
    "Conflicting payment result ignored" (değer yok). TransitionResult döner.
  - Readiness: catalog ile aynı `readinessState, db` (RabbitMQ yok; outbox tamponlar). Kök `/actuator/health` rabbit katkısını
    içerir → broker kapalıyken 503 DOWN ve RabbitHealthIndicator stack trace'li WARN yazar (catalog'da da aynı); compose/healthcheck
    readiness kullanmalı.
  - Testler (payment 247 = 220 − 1 amqp classpath parametresi + 1 readiness testi + 27): PaymentResultsTest 11, OutboxRelayIT 5,
    OutboxRelayDatabaseFailureTest 3, EventRoutingKeysTest 2, EventsExchangeCompatibilityTest 2, OutboxRelayBrokerOutageIT 1
    (kendi sabit portlu broker'ı; kesintide POST 201, readiness UP, satır bekler, broker dönünce yayın), OutboxDisabledTest 1,
    OutboxRelayUnknownEventTypeTest 1, OutboxSkipLockedTest 1. `ApiTestSupport` artık RabbitMQ konteynerini de import eder ve
    `@MockitoSpyBean OutboxService` taşır; TX proxy'li spy'a stub `AopTestUtils.getUltimateTargetObject(outbox)` üzerinden kurulur.
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 247. `spring-boot:run` (Docker RabbitMQ):
    health UP, readiness 200, WARN/ERROR 0; broker'da `kitapsepeti.events topic durable`.

- Payment Adım 5 — sağlayıcı webhook ucu `POST /webhooks/{provider}` (commit `26bfbb5` + `c1ac219`, push edildi; mock dispatcher, otomatik gönderim,
  kurtarma görevi YOK; migration, diğer modüller, common değişmedi; ham gövde DB'ye/loga yazılmaz). Kurallar systemPatterns
  "payment webhook":
  - Kod: `provider/mock/` MockWebhookSigner (Adım 6 dispatcher'ı da kullanacak) + MockWebhookVerifier; `config/WebhookSecurityConfig`
    (@Order 2; SecurityConfig @Order 3'e kaydı, `denyWithoutChallenge` ortak yardımcı); `controller/webhook/WebhookController`;
    `dto/webhook/WebhookEvent`; `service/WebhookService`; `exception/` WebhookSignatureException (401 + `Signature realm="webhook"`),
    WebhookRejectedException (ErrorCode alır); `PaymentErrorCode` + WEBHOOK_SIGNATURE_INVALID 401, UNKNOWN_PAYMENT 400, AMOUNT_MISMATCH
    400, PAYLOAD_TOO_LARGE 413 (API_CODES'ta payment kodlarının sonuna, INTERNAL_ERROR'dan önce); `PaymentProperties` `mock.webhookSecret`
    + `webhook(tolerance 5m, maxBodyBytes 65536)`; repository'lere `findByProviderReferenceForUpdate`, `existsByProviderTypeAndProviderEventId`;
    `.env.example` `PAYMENT_MOCK_WEBHOOK_SECRET`.
  - İmza biçimi (Adım 6 ve ileride iyzico için): `X-Mock-Timestamp: <epoch sn>`, `X-Mock-Signature: sha256=<64 küçük hex>`,
    HMAC-SHA256(key = secret UTF-8, "<ts>.<ham gövde baytları>"), ±5 dk (sınır dahil), sabit zamanlı karşılaştırma.
  - Kararlar: GET/PUT/PATCH/DELETE /webhooks/mock → 403 (405 değil; zincir yalnızca POST /webhooks/* açar). 404 sağlayıcı kontrolü
    Content-Type'tan da önce (`consumes` yerine elle 415). 413 için yeni PAYLOAD_TOO_LARGE kodu (common'da 413 kodu yok). Tutar JSON
    metni olmalı (katı okuyucu; sayı → 400 MALFORMED_REQUEST). Reddedilen olay (UNKNOWN_PAYMENT, AMOUNT_MISMATCH) provider_events'e
    yazılmaz; tekrar kontrolü tutar kontrolünden önce. Güvenlik ağı (uk ihlali → 204) controller'da (WebhookService.handle tek TX
    kalsın diye). UNKNOWN_PAYMENT/AMOUNT_MISMATCH WARN seviyesinde (imzalı ama tutarsız mesaj dikkat ister).
  - Testler (payment 297 = 247 + 50): MockWebhookSignatureTest 20 (unit, MutableClock), WebhookControllerTest 19, WebhookConcurrencyTest 3
    (10 paralel aynı olay → 1 kayıt/1 outbox; eşzamanlı succeeded+failed → 1 APPLIED + 1 CONFLICTING_FINAL; uk güvenlik ağı),
    PaymentPropertiesTest +7 (19; secret yok/boş/31 → açılmaz, değer yok; webhook varsayılanları; toString maskeli), SecurityRulesTest +1
    (9; zincir sırası). `ApiTestSupport` + `@MockitoSpyBean ProviderEventRepository`, secret kaydı.
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 297. `spring-boot:run` (.env secret'ıyla) →
    health UP, readiness 200; imzasız ve bozuk imzalı POST /webhooks/mock → 401 + `Signature realm="webhook"`; POST /webhooks/x → 404;
    GET /webhooks/mock → 403; logda yalnızca beklenen 3 WARN, gönderilen değerler yok. Pozitif uçtan uca Adım 6'da (secret okunmadı).

- Payment Adım 6 — mock'un kendi webhook'una otomatik gönderimi + kurtarma görevi; TAM AKIŞ ÇALIŞIYOR (oluştur → commit sonrası
  gecikmeli imzalı webhook → succeeded/failed → outbox → RabbitMQ). Commit `fbf586c` + memory-bank `b11d1ac`, push edildi. Migration, webhook ucu (Adım 5 sözleşmesi),
  diğer modüller, common değişmedi. Kurallar systemPatterns "payment mock webhook gönderimi" / "MockRecoveryJob":
  - Kod: `provider/mock/` MockPaymentReadyEvent, MockWebhookPayload, MockWebhookDispatcher, MockRecoveryJob; `config/MockWebhookConfig`;
    `SchedulingConfig` → `AnyJobEnabled` (outbox | mock recovery); `PaymentProperties.Mock` + `delay`, `webhookUrl`,
    `dispatch(enabled, queueCapacity)`, `recovery(enabled, interval, minAge, batchSize)` (+ doğrulama, toString); `PaymentTransactions`
    (ApplicationEventPublisher, olay yalnızca referans yazılınca ve mock'ta); `PaymentRepository.findStaleWithReference`; yml.
  - Kararlar: zamanlayıcı bean değil (bean olsaydı `@Scheduled` onu kullanırdı, `OutboxDisabledTest` TaskScheduler yokluğunu kilitler);
    kuyruk sınırı sayaçla (ScheduledThreadPoolExecutor kuyruğu sınırsız); `dispatch.enabled=false` göndericiyi kaldırmaz (kurtarma
    kullanır). `JdkClientHttpRequestFactory` (HttpURLConnection 401'de HttpRetryException). Kurtarma sayısı yalnızca DELIVERED.
    Test profilinde dispatch + recovery KAPALI, yalnızca Adım 6 akış testleri açar.
  - Testler (payment 339 = 297 + 42): MockWebhookDispatcherTest 14 (unit, JDK HttpServer stub + gerçek verifier: gövde/imza, failed,
    sabit eventId, SKIPPED halleri, 401 tekrar yok, 503, kapalı port, read timeout, DB hatası, URL yok, gecikme, kuyruk dolu, kapalı,
    log), MockWebhookFlowIT 8 (10.00 → succeeded + `mock_evt_<id>` + PaymentSucceeded mesajı messageId/payload; 10.99 → failed
    CARD_DECLINED + PaymentFailed; yanıt < gecikme; aynı orderId → ikinci gönderim yok; 5xx/4xx gerçek uca karşı; önceden final →
    SKIPPED; log), MockRecoveryJobIT 6 (seçim kuralları, batch + created_at sırası, yarış → 204 tekrar sayılar aynı, bir hata diğerlerini
    durdurmaz, boş tur log yok, EXPLAIN), MockDispatchDisabledIT 2 (initiated kalır; görev tamamlar), MockWebhookConfigTest 4,
    PaymentPropertiesTest +8 (27), OutboxDisabledTest güncellendi.
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 339. Yerel uçtan uca (spring-boot:run, Docker
    MySQL + RabbitMQ): geçici `payment.#` kuyruğu (konteyner içi rabbitmqadmin 2.35, kimlik bilgisi konteyner env'inden; classic
    transient non-exclusive kuyruk RabbitMQ 4'te yasak → durable açıldı, sonra silindi); 149.90 → 201 initiated, 2 sn sonra succeeded;
    149.99 → 201 initiated, 2 sn sonra failed CARD_DECLINED; kuyrukta PaymentSucceeded/payment.succeeded ×2 + PaymentFailed/
    payment.failed ×1 (fazla olan: kurtarma görevi önceki adımlardan kalan referanslı initiated bir ödemeyi tamamladı, logda
    `Mock recovery resent 1 webhook(s)`); gerçek DB EXPLAIN range/ix_payments_status_created/key_len 74; log: id, tutar, imza, referans
    yok, WARN/ERROR 0.

- Payment Adım 7 — OpenAPI (commit `db2a9fe` kod + `e36bca7` memory-bank, push edildi; Docker YOK; uç davranışı, DTO alan adları, durum kodları, migration, common ve
  diğer modüller, .env değişmedi). Kurallar systemPatterns "payment-service OpenAPI" + "Kart verisi kontrolü":
  - Kod: pom'a springdoc (kök BOM 3.1.1); yml `springdoc` bloğu (cart'la aynı; paths `/internal/**`, `/webhooks/**`) + `app.version`;
    `SecurityConfig` (@Order 3) docs/Swagger permitAll; `config/OpenApiConfig` (internalApiKey + mockWebhookSignature, PaymentStatus/
    Problem/FieldError şemaları, tek yol öneki customizer'ı); controller ve DTO anotasyonları; `docs/api/payment-service.openapi.json`.
  - Doküman: 3 yol / 3 operasyon; şemalar CreatePaymentRequest, PaymentResponse, PaymentStatus, WebhookEvent, Problem, FieldError;
    nullable yalnızca PaymentResponse.failureCode/redirectUrl. Kodlar: POST /internal/payments 200/201(Location)/400/401/409/500/503;
    GET /internal/payments/{paymentId} 200/400/401/404/500; POST /webhooks/{provider} 204/400/401/404/413/415/500.
  - Sapmalar: WebhookEvent.amount pattern gerçek doğrulamanınki (`^(0|[1-9][0-9]{0,9})\.[0-9]{2}$`, istenen `^\d+\.\d{2}$` değil);
    internal genel 406/415 ve webhook'ta pratikte ulaşılamayan 409 belgelenmedi (cart kuralı); providerPaymentId `minLength: 0`
    (@Size ezer; @NotBlank açıklamada; Adım 8'de `@Size(min = 1, max = 128)` ile 1'e çekildi); para alanlarında `example` yok.
  - Testler (payment 357 = 339 + 18): OpenApiContractTest 1 (bozma denemesi: failureCode tipi → yol + değerle kırıldı, geri alındı),
    OpenApiDocsTest 12 (gerçek hata yanıtları: 409/400/503/401 ApiKey, 404/400/401, webhook 404/415/413/401 Signature/400 ×4, 204),
    OpenApiRequiredFieldsTest 1 (201/200 POST, initiated/succeeded/failed GET), CardDataAbsenceTest 3 (negatif kontrol dahil),
    PaymentFlowLogHygieneIT 1 (tam akış + 401/409/401; id, tutar, imza, zaman damgası, sır, payload parçası yok), SecurityRulesTest
    +1 (`/v3/api-docs` artık açık → `/v3/api-docsx`, `/swagger-uix`), PaymentServiceApplicationTests −1 (springdoc parametresi silindi).
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 357. Yerel spring-boot:run: /v3/api-docs 200,
    3 operasyon, sözleşme dosyasıyla birebir; swagger-ui 200 (`/swagger-ui.html` → 302); WARN/ERROR 0.

- Payment Adım 8 — Docker + compose (commit `c735770` + memory bank `58914e0`, push'landı; diğer servislerin Dockerfile/compose kayıtları, common, migration'lar,
  `.env` ve `.env.example` değişmedi — `.env.example`'da tüm adlar zaten vardı). **PAYMENT SERVİSİ TAMAMLANDI.**
  - Ön düzeltme: `WebhookEvent.providerPaymentId` `@NotBlank @Size(min = 1, max = 128)` → sözleşmede `minLength: 1` (tek fark),
    drift testi yeşil. Görünür küçük fark: boş metin (`""`) artık iki `errors` girdisi (not blank + size) üretir; durum/kod aynı
    (400 VALIDATION_FAILED); null ve boşluk tek girdi.
  - `payment-service/Dockerfile`: cart'ın birebir kopyası (servis adı + `EXPOSE 8087`); ARG/ENV ile sır yok.
  - Compose `payment-service`: `127.0.0.1:8087:8087`; depends_on mysql + rabbitmq `service_healthy` (catalog'un seçimi; yalnızca
    açılış sırası — readiness RabbitMQ'ya bağlı değil); env tek tek (env_file yok): PAYMENT_DB_HOST=mysql, PAYMENT_DB_PORT=3306,
    PAYMENT_DB_USER/PASSWORD `${VAR:?}`, RABBITMQ_HOST=rabbitmq, RABBITMQ_PORT=5672, RABBITMQ_USER/PASSWORD (catalog gibi `${VAR}`),
    PAYMENT_INTERNAL_KEY_ORDER_SHA256 `${VAR:?}`, PAYMENT_MOCK_WEBHOOK_SECRET `${VAR:?}`; `webhook-url` yok (konteyner içi
    localhost:8087); restart unless-stopped, mem_limit 768m, readiness healthcheck (cart'la aynı).
  - Health: bileşenler db, rabbit, diskSpace, livenessState, readinessState, ping, ssl (Spring Cloud yok); readiness = readinessState
    + db; yalnızca `health` açık, ayrıntı yok. Kök `/actuator/health` broker kapalıyken 503 (compose readiness kullandığı için sağlıklı).
  - Testler (payment 365 = 357 − 7 + 15): yeni `config/ActuatorHealthTest` 15 (problar STRICT `{"status":"UP"}`, kök health ayrıntısız,
    8 actuator yolu 403 challenge'sız + internal key ile de 403, yalnızca `/actuator/health` eşlenmiş, POST 403, bileşen listesi
    birebir, rabbit readiness/liveness'ta yok; broker kapalıyken readiness UP → `OutboxRelayBrokerOutageIT`);
    PaymentServiceApplicationTests 3'e indi (health testleri taşındı); WebhookControllerTest validasyon haritasına `""` ve `"   "`.
  - `docs/docker.md` payment bölümü (port, env ADLARI, sabit compose değerleri, health, RabbitMQ kapalıyken davranış, mock akışı).
  - Root `clean verify`: common 25, user 83, catalog 276, cart 312 (2 skipped), payment 365. İmaj 604 MB, `id` → uid 10001(app).
  - Docker uçtan uca (geçici durable `payment.#` kuyruğu, sonra silindi): 149.90 → 201, 2 sn sonra succeeded; 149.99 → failed
    CARD_DECLINED; kuyrukta payment.succeeded/PaymentSucceeded + payment.failed/PaymentFailed. RabbitMQ durdurulunca payment healthy
    kaldı (readiness 200, kök 503), 149.80 → succeeded, 1 yayınlanmamış outbox satırı; RabbitMQ açılınca ~10 sn'de 0, mesaj kuyrukta
    (durable kuyruk restart'tan sağ çıktı). Chunked 70 KB (Content-Length yok, bozuk imza başlıkları) → 413 PAYLOAD_TOO_LARGE.
    Anahtarsız internal 401, GET /webhooks/mock 403, imzasız webhook 401 WEBHOOK_SIGNATURE_INVALID. `restart payment-service` →
    üç ödeme aynı durumda. İmaj: history'de sır yok (eşleşen 2 satır taban imajın JDK checksum'ı ve ubuntu config'i), Env adları
    PATH/JAVA_HOME/LANG/LANGUAGE/LC_ALL/JAVA_VERSION/JAVA_TOOL_OPTIONS, find taramasında yalnızca `/usr/lib/ssl/cert.pem` (sistem CA
    paketine openssl symlink'i; cart imajında da aynı). Log: tutar/referans/imza/sır/özet yok; kesintide 12 OutboxRelay WARN
    (AmqpIOException ×10, AmqpConnectException ×2) + 1 RabbitHealthIndicator WARN (kök health çağrısı) — OutboxRelay WARN'ı outbox
    `id` (= eventId) ve eventType yazar (bilinen artık iş); diğer WARN'lar beklenen reddetmeler (401/403/imza).

- ORDER FAZI BAŞLADI. Order Adım 0a — common sertleştirme (commit `298fe89`, push'landı; outbox, CB, order modülü, migration YOK; uç durum
  kodları/gövdeleri değişmedi, yalnızca `instance`'taki id maskelenir). Ayrıntı systemPatterns "common modülü".
  - A) Yol maskeleme: YENİ `common.web.RequestPathMasker` (segment yapısıyla desen eşleme, desen dışı UUID → `:id`, sorgu yok, her
    zaman geçerli URI). `ProblemDetails`/`ProblemDetailExceptionHandler` (opsiyonel bean, `logProblem`/`respond`), security
    entry point/access denied/failure handler, internal entry point ve filtre (INFO + 401 satırı) maskeleyiciyi kullanır.
    Desenler servislerin `SecurityConfig.requestPathMasker()` bean'inde (user 1, catalog 13, cart 1, payment 1).
    Silinen: cart `MaskedRequestPaths` + handler override'ları + SecurityConfig sarmalayıcıları; payment `MaskedRequestPaths` +
    `InternalSecurityConfig.MaskedPathFilter` + override'lar. Cart `CartViewAssembler` maskeleyiciyi ctor'dan alır.
  - B) Internal özet politikası common `InternalApiKeys`'te tek kural (yok/boş/boşluk/64 hex değil → açılmaz, değer mesajda yok);
    `Client.enabled()` ve "boş = kapalı" kaldırıldı. cart/payment `InternalAuthConfig` yalnızca bean; catalog'a YENİ
    `config/InternalAuthConfig` (bean `InternalSecurityConfig`'ten taşındı). Compose `CATALOG_INTERNAL_KEY_ORDER_SHA256` artık
    `${VAR:?}`; `.env.example` yorumları, catalog application.yml yorumu, `docs/api/catalog-internal-stock.md` güncellendi.
  - DAVRANIŞ DEĞİŞİKLİKLERİ: catalog ve user `instance`/loglarında id'ler maskeli (ör. `/api/books/:bookId`,
    `/internal/stock/reservations/:orderId/commit`, `/api/me/addresses/:addressId`); tüm servislerde desen dışı yollarda UUID → `:id`
    (güvenlik ağı); ayrıştırılamayan yol artık `instance` yok yerine yüzde-kodlu yol; catalog boş özetle AÇILMAZ.
    OpenAPI: catalog ve user `Problem.instance` açıklaması (tek satır fark her sözleşmede).
  - Testler: common 25 → 39 (RequestPathMaskerTest 10 yeni; ProblemDetailsTest 4 → 6; InternalApiKeysTest 5 → 7 — "boş özet kapalı"
    testi silindi, eksik/boş/boşluk, istemcisiz, iki harf düzeni, çoklu istemci eklendi; filtre testi 5 → 5 — devre dışı istemci
    testi yerine maskeli log/instance testi). user 83 → 85 (YENİ `controller/AddressPathMaskingTest` 2: 404/401 maskeli, bozuk id
    400 maskeli, logda id yok). catalog 276 → 284 (YENİ `config/InternalAuthConfigTest` 4; YENİ `controller/internal/IdPathMaskingTest`
    4: rezervasyon 404/401/409 maskeli + logda orderId yok, public/admin 404 + 401 + "abc" + lookup + desen dışı `:id`);
    `GlobalExceptionHandlerTest.staleVersion...` log beklentisi `/books/:id/stale-update`. cart 312 → 312 (`CatalogGatewayTest` probe
    yolu `/api/cart/_catalog/books/:id` beklentisi + id artık hiçbir log/gövdede yok; `CartItemChangesTest` maskeleme testleri
    DEĞİŞMEDEN yeşil). payment 365 → 365 (InternalAuthConfigTest'te servis adı beklentisi kaldırıldı; maskeleme testleri değişmedi).
  - Root `clean verify` yeşil: common 39, user 85, catalog 284, cart 312 (2 skipped), payment 365.
  - Docker: 4 imaj yeniden derlendi, `up -d` → hepsi healthy, restart 0 (catalog özetle açıldı). Canlı: catalog anahtarsız
    `POST /internal/stock/reservations/<uuid>/commit` → 401, instance `/internal/stock/reservations/:orderId/commit`; cart bilinmeyen
    kitap PATCH → 404 `/api/cart/items/:bookId`; iki servisin loglarında UUID 0.

- Order Adım 0b — outbox → common (commit `df98e92` + docs `80dea5c`, push edildi; davranış, payload, routing key, mesaj özellikleri,
  migration DEĞİŞMEDİ).
  Ayrıntı ve yeni servis tarifi: systemPatterns "Ortak outbox".
  - Karşılaştırma: Relay/Publisher/Properties/PublishException/Repository kodu, DDL (yorumlar hariç), `app.outbox` yml, mesaj özellikleri,
    log metinleri üç serviste aynıydı. Korunan farklar: payment'ın "id payload'dan önce" yaklaşımı ortak API oldu (`persist`); her servis
    kendi `SchedulingConfig`'ini tutar (catalog: outbox VEYA stok süre dolumu; payment: outbox VEYA mock kurtarma).
  - Entity + repository COMMON'da (DDL'ler aynı); servis ana sınıfında
    `@AutoConfigurationPackage(basePackageClasses = { <Uygulama>.class, OutboxEvent.class })`. İlk denemede yalnızca OutboxEvent yazıldı →
    servisin repository'leri bulunmadı (doğrudan anotasyon varsayılan paketin yerine geçiyor); düzeltildi.
  - Servislerden silinen (×3): `entity/OutboxEvent`, `repository/OutboxRepository`, `service/OutboxService`, `outbox/OutboxRelay`,
    `outbox/OutboxProperties`, `outbox/OutboxPublishException`, `config/RabbitConfig`. Kalan: `outbox/EventRoutingKeys`
    (`OutboxRoutingKeys.of`), `outbox/OutboxPublisher` (common'ın ince alt sınıfı; testler aynı tipi kullanmaya devam etsin diye),
    YENİ `config/OutboxConfig`. `AuthService`/`BookAdminService`/`StockReservationTransactions`/`PaymentResults` yalnızca import.
  - Log: kategori `com.kitapsepeti.common.outbox.*` (metin aynı). user + catalog yml'ine `CachingConnectionFactory: WARN` (payment'taki gibi).
  - Masker: `RequestPathMasker.patterns()` + `covers()`; dört serviste `config/RequestPathMaskerCoverageTest` (main code source filtresi;
    payment istisnası `/webhooks/{provider}`). Kırma denemesi: cart'a geçici `GET /api/cart/_coverage/{probeId}` → test
    `Expecting empty but was: ["/api/cart/_coverage/{probeId}"]` ile kırıldı, geri alındı.
  - Testler: common 39 → 60 (OutboxRelayTest 6, OutboxPublisherTest 5, OutboxServiceTest 4, OutboxConfigurationTest 3,
    OutboxRoutingKeysTest 1, RequestPathMaskerTest +2). user 85 → 86, catalog 284 → 285, cart 312 → 313 (+1 kapsama testi her biri);
    payment 365 → 367 (+1 kapsama, +1 `outboxDdlIsIdenticalToUsers`). Servis testlerinde yalnızca import değişti (+ iki
    EventsExchangeCompatibilityTest javadoc'u). Root `clean verify` yeşil.
  - Docker: user/catalog/payment yeniden derlendi, healthy. Geçici kuyruk (`#`) ile: kayıt → `UserRegistered`/`user.registered`;
    payment internal 149.90 → `PaymentSucceeded`/`payment.succeeded` (anahtar kullanıcı onayıyla .env'den yalnızca belleğe okundu);
    catalog admin token yok → üç DB'de yayınlanmamış outbox 0. Kuyruk silindi. Üç serviste "Created new connection"/`amqp://<kullanıcı>@` 0.
    Yerel user_db'de 1 yeni e2e kullanıcısı, payment_db'de 1 yeni ödeme.

- Order Adım 1 — order-service iskeleti + order_db (commit `f190299`, push edildi). Ayrıntı: systemPatterns "order-service".
  - DB: `infra/mysql/init/40-order-db.sh` (30-payment kalıbı), compose mysql env'ine `ORDER_DB_USER/PASSWORD` (`${VAR:?}`),
    `.env.example`. Script mevcut volume'da 3 kez çalıştırıldı (idempotent); order kullanıcısı yalnızca `order_db` görür
    (`USAGE ON *.*` + `ALL ON order_db.*`). order-service compose'a EKLENMEDİ (Adım 11); Dockerfile yok.
  - Modül: kök pom'a `<module>order-service</module>`; port 8088, paket `com.kitapsepeti.order`. Bağımlılıklar payment ile aynı +
    `security-oauth2-resource-server` (cart'taki JWT doğrulaması); openfeign/resilience4j YOK (testle kilitli, Adım 3).
  - Kod: `OrderServiceApplication` (`@AutoConfigurationPackage({ App, OutboxEvent })`, UserDetailsService auto-config hariç),
    `config/{ClockConfig, OutboxConfig, SchedulingConfig, JwtDecoderConfig, SecurityConfig}`, `outbox/{EventRoutingKeys (BOŞ eşleme),
    OutboxPublisher}`, `security/{JwtProperties, JwtSubjects}`, `exception/GlobalExceptionHandler` (boş). Sipariş entity/repository/
    controller YOK (Adım 2/4). `RequestPathMasker.uuidOnly()` (desen yok; `/api/orders/{orderId}` Adım 4'te).
  - Security: `/api/**` authenticated (USER/ADMIN), health + docs + `/error` permitAll, geri kalan denyAll (kimliksiz 401 Bearer
    challenge, token'lı 403). Internal zincir YOK.
  - V1 `V1__init_order.sql`: orders, order_items, order_status_history, outbox (DDL user/payment ile birebir).
  - Testler 164: OrderSchemaConstraintsTest 114, SecurityRulesTest 26, ActuatorHealthTest 15, OrderServiceApplicationTests 7,
    RequestPathMaskerCoverageTest 1, StartupLogHygieneTest 1 (ayrı MySQL, çalışma anında üretilen DB/Rabbit kimlikleri; açılış
    logunda kullanıcı adı/parola, `jdbc:...@`, `user=/password=` yok). Not: `SpringApplicationBuilder` testte `.main(App.class)`
    ister, yoksa "Started ForkedBooter" yazar.
  - Root `clean verify` yeşil: common 60, user 86, catalog 285, cart 313 (2 skipped = CatalogLiveTest, `66b3d01`'den beri; 0b ile
    ilgisiz), payment 367, order 164.
  - Yerel `spring-boot:run` (`.env` `spring.config.import` ile süreç içinde): V1 uygulandı, readiness UP, `/api/orders` token'sız 401,
    WARN/ERROR yok; `flyway_schema_history` v1 success=1; tablolar flyway_schema_history, order_items, order_status_history, orders,
    outbox. Servis durduruldu.
  - Bulgular: Cart DB üst sınırı `ck_cart_items_quantity BETWEEN 1 AND 99` (iş kuralı `app.cart.max-quantity-per-item: 10`) →
    order_items aynı 1–99. Catalog `price_amount >= 0` (ücretsiz kitap) ama `orders.total_amount > 0` ve `subtotal > 0`:
    tamamı ücretsiz sepetin siparişi şemada reddedilir (kısıt BİLEREK değiştirilmedi; Adım 4'te checkout'ta açık hata kodu
    gerekecek, ör. `ORDER_TOTAL_ZERO`, ya da ürün kararı).

- Order Adım 2 — domain: entity'ler + durum makinesi + repository (commit `51feb91`, push edildi). Ayrıntı ve geçiş
  tablosu: systemPatterns "Order domain'i". V1 değişmedi, ddl validate ek düzeltmesiz geçti; controller/DTO/Feign/consumer YOK.
  - `entity/`: `DbEnum`, `StrictDbEnumConverter`, `OrderStatus` (+Converter), `StockState` (+Converter), `TransitionResult`,
    `OrderReasons`, `OrderRuleViolation` (+`Code`), `OrderLine` (record), `AddressSnapshot` (record, toString redacted),
    `AddressSnapshotConverter`, `Order`, `OrderItem`, `OrderStatusHistory`. `repository/OrderRepository`.
  - PROJE KARARI: tamamı ücretsiz sepet → checkout sipariş yazılmadan ÖNCE `422 ORDER_TOTAL_ZERO` (domain kuralı `Order.place`'te;
    HTTP eşlemesi Adım 4/5).
  - JSON kararı: `@JdbcTypeCode(SqlTypes.JSON)` doğrudan record'a DENENDİ (geçici probe testi, silindi): çalışıyor ama Hibernate
    springdoc'un getirdiği Jackson 2 mapper'ını örtük seçiyor, eksik alan sessizce null; Jackson 3'e geçerse bilinmeyen alan da
    sessizce atlanır → açık `AddressSnapshotConverter` (sabit 8 alan, bilinmeyen/eksik/tekrar → hata).
  - Reason/failure kodları (`OrderReasons`, şimdilik yalnızca sabit): ORDER_PLACED, PAYMENT_SUCCEEDED, OUT_OF_STOCK,
    CATALOG_UNAVAILABLE, PAYMENT_UNAVAILABLE, CARD_DECLINED, PAYMENT_FAILED, ORDER_EXPIRED.
  - Testler order 164 → 315 (+151): OrderTransitionsTest 74 (tablo: her metot × 10 ulaşılabilir başlangıç durumu), OrderPlaceTest 32,
    AddressSnapshotConverterTest 25, OrderRepositoryTest 14 (`@DataJpaTest`; round-trip, küçük harf, JSON OBJECT, INSERT kolonları
    `SqlCapture` StatementInspector ile, uk_orders_pending_user, paid/failed flush + history sayıları, sahiplik, bilinmeyen/eksik JSON
    alanı okumada hata), OrderLockingTest 3 (FOR UPDATE bekler, 5 sn sonra `PessimisticLockingFailureException`, düz okuma beklemez),
    PiiToStringTest 3 (`OrderLine` toString de redacted). Root `clean verify` yeşil: common 60, user 86, catalog 285, cart 313
    (2 skipped), payment 367, order 315. Beklenen değişiklik: `OrderServiceApplicationTests` "yalnızca OutboxEvent" testi →
    `orderAndOutboxEntitiesAndRepositoriesAreMapped` (Order, OrderItem, OrderStatusHistory, OutboxEvent; OrderRepository + OutboxRepository).
  - Sapmalar: fiyat ölçeği değer bazlı (10.000 kabul); boş/uzun başlık ve toplam üst sınırı IAE; markStockReleased PENDING'de
    CONFLICTING; markStockCommitted PAID+REQUESTED'de ISE; markStockHeld FAILED+REQUESTED'de APPLIED (spec'e uygun, stok sonra
    bırakılır); ek repository metodu `findByUserIdAndStatus`.

- Order Adım 3a — Cart/Catalog/Payment istemcileri + gateway'ler + circuit breaker (commit `41d202c` + `cb759ea`, push edildi; checkout servisi,
  controller, consumer, scheduler YOK; diğer servisler ve V1 değişmedi). Ayrıntı: systemPatterns "Order istemcileri".
  - Bağımlılıklar (BOM'dan sürümsüz): `spring-cloud-starter-openfeign`, `feign-java11`, `spring-cloud-starter-circuitbreaker-resilience4j`;
    test `org.wiremock:wiremock-standalone:3.13.1` (jar incelendi: Jetty/Jackson/Guava/jakarta `wiremock.` altına taşınmış; Spring
    Boot 4 + Jackson 3 ile çakışma yok; istek sayma/doğrulama, gecikme, hata enjeksiyonu). MockWebServer yerine: istek eşleme +
    sayım + gecikme/fault tek araçta.
  - Kod: `gateway/` (CartGateway, CatalogGateway, PaymentGateway; sealed CartSnapshotResult, BookLookupResult, ReserveResult,
    CommitResult, ReleaseResult, PaymentInitiationResult; ortak record'lar NotPerformed, Unknown, Unavailable, Rejected; StockLine,
    CatalogBook, ReservationStatus, PaymentState), `client/` (Downstream, InternalApiKey(+Interceptor), InternalClientConfiguration,
    ProblemErrorDecoder, RemoteProblemException, InvalidResponseException, CircuitBreakerProperties, DownstreamCircuitBreakers,
    CallOutcome, RemoteCalls, ClientHeaders; `cart/`, `catalog/`, `payment/` alt paketlerinde Feign arayüzü + DTO + Feign*Gateway),
    `config/ClientConfig` (`@EnableFeignClients`, anahtar bean'i). application.yml: `app.clients.*`, `app.circuit-breaker.*`,
    `spring.cloud.*` (openfeign, CB kapalı, discovery health kapalı), `management.health.{refresh,circuitbreakers}.enabled=false`.
  - Sözleşme keşfi (kod okumayla): Cart snapshot her zaman 200 (aktif sepet yok → cartId null + []); Catalog lookup PUBLIC, ≤50 id,
    yalnızca yayındakiler; reserve 201/200 (aynı küme → mevcut durum, committed/released dahil)/409 MISMATCH/INSUFFICIENT_STOCK/
    BOOK_NOT_AVAILABLE+bookIds; commit 200 (idempotent; süresi geçmiş ama held de commit edilir)/409 RELEASED/404; release 200/409
    COMMITTED/404 (bilinmeyen sipariş, Catalog kodunda doğrulandı); Payment 201/200 (aynı istek → mevcut ödeme, her durum)/409
    PAYMENT_ORDER_MISMATCH/503 PAYMENT_PROVIDER_UNAVAILABLE. Order'ın kullandığı her uç OpenAPI'de (markdown fixture gerekmedi).
  - Belirsizlik/raporlananlar: Catalog `ReservationResponse.status` kodda "mixed" üretebilir (OpenAPI'de yok; Order → Unknown);
    `.env.example`'da `ORDER_CART_URL/ORDER_CATALOG_URL/ORDER_PAYMENT_URL` yok (varsayılanlar localhost; dokunulmadı); Catalog/Payment
    GET uçları istemcide yok (Adım 5'te Unknown'ı netleştirmek için gerekebilir).
  - Testler order 315 → 426 (+111): CatalogGatewayTest 38, CartGatewayTest 23, PaymentGatewayTest 17, ClientContractTest 10,
    CircuitBreakerTest 8, InternalApiKeyTest 6, RemoteUnreachableTest 3 (bağlantı reddi → NotPerformed/Unavailable; readiness UP),
    GatewayTypesTest 3, ClientLoggingTest 2, ClientHeadersTest 1 (token'lı RequestContext + SecurityContext; Authorization/Cookie hiçbir
    istekte yok). Beklenen değişiklik: `feignAndResilience4jAreNotOnClasspath` → `springCloudCircuitBreakerLayersAreDisabled`;
    StartupLogHygieneTest çalışma anında üretilen anahtarı verir ve logda arar. Root `clean verify` yeşil: common 60, user 86,
    catalog 285, cart 313 (2 skipped), payment 367, order 427 (raporda 426 yazılmıştı; kök verify logu 427, 3b'de doğrulandı).
  - Sapmalar: JDK HttpClient (feign-java11); anahtar yalnızca `/internal/` yollarına (lookup public); ek sonuç tipleri NotHeld,
    AlreadyReleased, AlreadyCommitted, Rejected, Initiated'da PaymentState; 4xx CB'de "yok sayılmadı", BAŞARI sayıldı (karşı taraf
    ayakta); Spring Cloud CircuitBreakerFactory yerine Resilience4j doğrudan; commit 404 → Rejected (Released değil); Payment GET yok.

- Order Adım 3b — Cart→Catalog circuit breaker + CB kurulumu common'a (COMMIT EDİLMEDİ; Catalog/Payment/User kodu, migration,
  Cart API/hata kodları/OpenAPI değişmedi). Ayrıntı: systemPatterns "common.resilience" ve "Servisler arası HTTP istemcisi".
  - Ortak kod KARARI: taşındı, ama yalnızca genel kısım — `common.resilience.CircuitBreakerProperties` (Order'daki record aynen) +
    `CircuitBreakers` (config + WARN durum logu). Sınıflandırma servislerde: Order sonuç tabanlı (NotPerformed/Unknown ayrımı,
    `RemoteCalls`), Cart istisna tabanlı (`CatalogGateway.guarded`); ortak bir "çağrı sarmalayıcı" Order'ı karmaşıklaştırırdı. Order:
    `client/CircuitBreakerProperties` silindi, `DownstreamCircuitBreakers` common'ı kullanıyor, log satırı aynı (logger adı common);
    Order pom'u ve testleri DEĞİŞMEDİ (hâlâ spring-cloud-starter-circuitbreaker-resilience4j + kapatma ayarları; sadeleştirme opsiyonel).
  - Cart: pom'a `resilience4j-circuitbreaker` (BOM'dan), `config/CatalogCircuitBreakerConfig`, `app.circuit-breaker.*` (Order ile aynı
    değerler), `CatalogGateway` sarıldı; Feign istemcisi aynı, retry YOK (`Retryer.NEVER_RETRY`, mevcut test). Test altyapısı:
    `ApiTestSupport` `@AfterEach catalogCircuitBreaker.reset()` (beklenti değişmedi).
  - Testler: common 60 → 65 (CircuitBreakersTest 5), cart 313 → 321 (CatalogCircuitBreakerTest 7: 10 teknik hata [503/500/bozuk 2xx]
    → açık + istek gitmiyor, açıkken ekleme/görünüm yanıtı kapalıdaki "Catalog yok" ile byte-byte aynı, 404 açmıyor, inStock=false ve
    400 başarı, half-open 3 deneme → kapalı, half-open hatası → yeniden açık, açık + Catalog 503 → readiness UP; CatalogDownCircuitBreakerTest 1:
    kapalı port → 10 bağlantı hatası açar, sonraki çağrı <100 ms CallNotPermitted, readiness UP). Root `clean verify` yeşil: common 65,
    user 86, catalog 285, cart 321 (2 skipped), payment 367, order 427 (değişmedi, sınıf bazında aynı).
  - Docker (cart rebuild, catalog stop): 12 × `GET /api/cart` → 1–5: 200 UNAVAILABLE ~1030 ms (connect-timeout 1 sn; durdurulmuş
    container'ın IP'si yok), devre 5. istekte açıldı (pencerede kurulumdan 5 başarı vardı: 5/10 = %50), 6–12: 200 UNAVAILABLE ~24 ms.
    Log: `Circuit breaker catalog CLOSED -> OPEN` (WARN, id yok); açıkken `cause=CallNotPermittedException`. Catalog start (13 sn'de
    healthy) → ilk istekte OPEN -> HALF_OPEN, 3 deneme sonrası HALF_OPEN -> CLOSED; 200 VERIFIED 38–188 ms.
  - Keşif (Adım 5/6/8 için zamanlamalar, kod okumayla):
    - Catalog rezervasyon süresi `app.stock.reservation-ttl` 15m (`StockProperties`; `expiresAt = now + ttl`, MICROS). Süre dolumu
      `ReservationExpiryJob` (`app.stock.expiry.enabled` true, `interval` 30s fixedDelay, `batch-size` 100): `held` + `expires_at < now`
      siparişleri kendi TX'inde `released` yapar (iptal ucuyla aynı kod; SKIP LOCKED, kısmi kilitte atlar). Fiilî serbest bırakma
      TTL + en çok ~30 sn (+ batch kuyruğu). Görev çalışmadan önce süresi geçmiş ama `held` rezervasyon commit EDİLEBİLİR.
    - Payment kurtarma `MockRecoveryJob` (`app.payment.mock.recovery.enabled` true, `interval` 30s fixedDelay + initialDelay 30s,
      `min-age` 10s, `batch-size` 50): `initiated` + mock + sağlayıcı referanslı ve `created_at < now - 10s` ödemelerin webhook'unu
      yeniden üretir. Referanssız `initiated` (sağlayıcı çağrısı başarısız) dokunulmaz → Order'ın aynı isteği tamamlar. Normal yol
      mock `delay` 500ms. Kurtarılan ödeme en geç ~10 sn + 30 sn içinde sonuçlanır.
  - Sapmalar: devre açıkken Cart logundaki neden adı `CallNotPermittedException` (yanıt aynı); Docker'da devre 12 isteğin 10.'unda değil
    5.'inde açıldı (pencere kurulum çağrılarını da sayar; kural aynı); Order baz sayısı 426 değil 427.
  - (3b commit edildi: `f651c07` + `8e3b037`.)

- Order Adım 4 — checkout mutlu yol + `GET /api/orders/{orderId}` (COMMIT EDİLDİ: `867ce0f` kod, `c8314f0` memory-bank).

- Order Adım 5 — Telafi (Compensation & Interrupted Checkout Handling):
  - Kural: Para işin içindeyse sipariş failed YAPILMAZ; stok işin içindeyse hemen release denenir.
  - Satır içi release (`CheckoutService.releaseStock`):
    - Tetiklenme: Kayıt sonrası sipariş failed yapılan tüm durumlar (reserve: Insufficient, NotSellable, NotHeld, Rejected, NotPerformed, Unknown; payment: NotPerformed, Rejected).
    - Çağrı TX DIŞINDA: Catalog `release(orderId)` tek tip çağrı (Insufficient'ta Catalog 404 RESOURCE_NOT_FOUND → Released döner).
    - Sonuca göre:
      - `Released` → TX (FOR UPDATE): `markStockReleased(orderId)` (stock_state = `released`). APPLIED değilse WARN, exception yok.
      - `AlreadyCommitted` → ERROR logu (olmaması gereken durum), stok durumu değişmez.
      - `NotPerformed` / `Unknown` / `Rejected` → stok requested/held kalır, WARN (Adım 6 StockSyncJob ve Adım 8 toplar).
    - HTTP yanıtı release sonucundan ETKİLENMEZ (Adım 4 hata kodları aynen korunur).
  - Yarıda kesilme senaryoları (OrderTransactions beklenmeyen exception / DB hatası):
    | Senaryo | Durum / Hata | Yanıt | DB'de Kalan Durum | Adım 8 Kalıntı Toplama |
    |---|---|---|---|---|
    | **2.a** | Rezervasyon Reserved ama `markStockHeld` başarısız | 503 CHECKOUT_INTERRUPTED + orderId | markFailed başarılıysa failed + released; markFailed başarısızsa pending + requested | pending + requested → CHECKOUT_INTERRUPTED + release |
    | **2.b** | Payment Initiated ama `attachPayment` başarısız (`uk_orders_payment` ihlali veya genel DB) | 201 Created + pending (sipariş okunamıyorsa 503 CHECKOUT_INTERRUPTED + orderId) | pending + held, paymentId null (sipariş failed YAPILMAZ) | pending + held → Payment initiate tekrarı |
    | **2.c** | Reserve başarısızlığı sonrası `markFailed` başarısız | 409 / 503 (Adım 4 kodu) + orderId | pending + requested (release denenir, stok durumu yazılamaz) | pending + requested → CHECKOUT_INTERRUPTED + release |
    | **2.d** | Payment NotPerformed/Rejected sonrası `markFailed` başarısız | 503 PAYMENT_UNAVAILABLE + orderId | pending + held (release DENENMEZ; sipariş pending görünüyor) | pending + held → Payment initiate tekrarı |
    | **2.e** | TX1 (insert) uk dışı bir DB hatası | 503 ORDER_UNAVAILABLE (orderId yok) | Satır yok (hiçbir dış çağrı yapılmaz) | Kalıntı yok |
  - Log hijyeni: Tüm telafi yollarında tek satır log `Stock compensation -> <ResultName> (durationMs=n)`. id, tutar, kitap id'si, adres bilgisi bulunmaz.
  - Güncellenen Hata Tablosu (durum × HTTP × code × sipariş yazıldı mı × stock_state):
    | Durum | HTTP | code | Sipariş | stock_state |
    |---|---|---|---|---|
    | Bekleyen sipariş var (`uk_orders_pending_user`) | 409 | ORDER_PENDING_EXISTS (+orderId) | Hayır | — |
    | Sepet yok/boş | 422 | CART_EMPTY | Hayır | — |
    | Cart erişilemez | 503 | CART_UNAVAILABLE | Hayır | — |
    | Lookup erişilemez (CB açık dahil) | 503 | CATALOG_UNAVAILABLE | Hayır | — |
    | Kitap bulunamadı / stokta değil (lookup) | 409 | BOOK_NOT_AVAILABLE | Hayır | — |
    | Farklı para birimleri | 422 | MIXED_CURRENCY | Hayır | — |
    | Toplam 0 | 422 | ORDER_TOTAL_ZERO | Hayır | — |
    | Toplam > 9999999999.99 | 422 | ORDER_TOTAL_TOO_LARGE | Hayır | — |
    | Diğer Order.place ihlalleri | 422 | INVALID_PRICE / INVALID_CURRENCY / INVALID_QUANTITY / DUPLICATE_BOOK / EMPTY_ORDER | Hayır | — |
    | TX1 (insert) DB hatası (2.e) | 503 | ORDER_UNAVAILABLE | Hayır | — |
    | Doğrulama / bozuk JSON / token yok | 400 / 400 / 401 | VALIDATION_FAILED / MALFORMED_REQUEST / UNAUTHORIZED | Hayır | — |
    | Reserve Insufficient | 409 | INSUFFICIENT_STOCK (+orderId) | Evet, failed OUT_OF_STOCK | released (Catalog 404 → Released) |
    | Reserve NotSellable | 409 | BOOK_NOT_AVAILABLE (+orderId) | Evet, failed BOOK_NOT_AVAILABLE | released (release başarılıysa) / requested (başarısızsa) |
    | Reserve NotHeld / Rejected / NotPerformed / Unknown | 503 | CATALOG_UNAVAILABLE (+orderId) | Evet, failed CATALOG_UNAVAILABLE | released (release başarılıysa) / requested (başarısızsa) |
    | markStockHeld başarısız (2.a) | 503 | CHECKOUT_INTERRUPTED (+orderId) | Evet, failed CHECKOUT_INTERRUPTED | released (release başarılıysa) / requested (markFailed çökerse pending+requested) |
    | Payment NotPerformed | 503 | PAYMENT_UNAVAILABLE (+orderId) | Evet, failed PAYMENT_UNAVAILABLE | released (release başarılıysa) / held (başarısızsa) |
    | Payment Rejected | 503 | PAYMENT_UNAVAILABLE (+orderId) | Evet, failed PAYMENT_REJECTED | released (release başarılıysa) / held (başarısızsa) |
    | Payment attachPayment DB hatası (2.b) | 201 (veya 503 CHECKOUT_INTERRUPTED) | — (veya CHECKOUT_INTERRUPTED) | Evet, pending + held | held (failed YAPILMAZ; release çağrılmaz) |
    | Payment Unknown (WARN) | 201 | — | Evet, pending + held, paymentId null | held (release çağrılmaz) |
    | Mutlu yol | 201 | — | Evet, pending + held + paymentId | held |
    | GET başkasının / olmayan | 404 | ORDER_NOT_FOUND | — | — |
  - PLAN DEĞİŞİKLİĞİ: Adım 8 Pending Uzlaştırma Planı:
    - `held` ise: Payment'a aynı initiate isteğini atar (dönen succeeded/failed → olayla aynı geçiş; kaybolan RabbitMQ olaylarını da kapatır).
    - `requested` ise: `CHECKOUT_INTERRUPTED` + Catalog release yapar.
    - 10 dk sonunda: `ORDER_EXPIRED` + Catalog release yapar.
    (Bu adımda scheduler/job YOK; yalnızca bu adımın bıraktığı durumlar Adım 8 tarafından tutarlı biçimde toplanır).
  - Kalıntı Durumları Listesi:
    - `pending + requested`: (1) Reserve öncesi veya sırasında kesinti (2.a'da markFailed çökerse, 2.c'de markFailed çökerse). Adım 8 CHECKOUT_INTERRUPTED + release ile kapatır.
    - `pending + held`: (1) Payment Unknown, (2) attachPayment DB hatası (2.b), (3) Payment NotPerformed/Rejected sonrası markFailed çökmesi (2.d). Adım 8 Payment'a sorarak (initiate tekrarı) kapatır.
    - `failed + requested/held`: Catalog release çağrısının NotPerformed/Unknown/Rejected döndüğü durumlar. Adım 6 StockSyncJob (veya periyodik stok eşleme) tarafından Catalog release tekrarı ile kapatılır.
  - Testler (order 489 → 541, +52):
    - `CheckoutReleaseCompensationTest` (42 test): 8 kayıt sonrası hata türü × 5 release sonucu matrisi (40 parametreli test) + 404 RESOURCE_NOT_FOUND eşlemesi (1 test) + hassas bilgi içermeyen log hijyeni (1 test).
    - `CheckoutInterruptedTest` (10 test): 2.a markStockHeld çökmesi (markFailed başarılı ve başarısız), 2.b attachPayment çökmesi (uk_orders_payment ve genel DB, sipariş okunabilir/okunamaz), 2.c reserve sonrası markFailed çökmesi, 2.d payment sonrası markFailed çökmesi (NotPerformed ve Rejected), 2.e insert genel DB ve kısıt hataları.
  - Adres kuralları user-service `AddressRequest`'ten (`dto/request/AddressRequest`): recipientName/phone/line1/city @NotBlank (120/32/
    200/80), line2 200, district 80, postalCode 16, country `^[A-Z]{2}$` + @NotNull (user'da null → TR; burada zorunlu). Telefon biçim
    kuralı yok (user'da da yok). Doğrulama hatası değer yansıtmaz (`errors[].field` + mesaj).
  - Yeni: `OrderTotalTooLargeException` (IAE alt tipi), `OrderReasons.BOOK_NOT_AVAILABLE` / `PAYMENT_REJECTED`, `OrderErrorCode`,
    `OrderProblemException(code, orderId?)`, `@CurrentUserId` + resolver + `WebConfig` (cart kopyası), `OrderTransactions`,
    `OrderQueryService`, `CheckoutService`, `OrderController`, DTO'lar. `SecurityConfig` masker `/api/orders/checkout` + `/api/orders/{orderId}`.
  - Değişen mevcut testler: `OrderServiceApplicationTests.noApplicationControllersYet` → `onlyOrderControllerIsExposed`;
    `SecurityRulesTest` 404 örneği `/api/orders/olmayan-yol` → `/api/orders/olmayan/yol` (tek segment artık `{orderId}` → 400).
  - Testler (order 427 → 489, +62): CheckoutHappyPathTest 5 (DB/history/kalemler, reserve/payment gövdeleri, amount JSON sayı 2 ondalık,
    hiçbir alt isteğe Authorization yok, Initiated+succeeded yok sayılır, opsiyonel adres boş → null, bilinmeyen alan yok sayılır),
    CheckoutBeforeOrderFailuresTest 33 (tüm kayıt öncesi kodlar + doğrulama; reserve/payment çağrılmadı, satır yok), CheckoutAfterOrderFailuresTest 12
    (tüm reserve/payment varyantları, history pending→failed, orderId yanıtta), CheckoutPendingOrderTest 4 (eşzamanlı 2 checkout → 1×201 +
    1×409 kazananın id'siyle, tek satır, tek reserve; pending → 409 → failed sonrası 201), OrderQueryTest 6, CheckoutLoggingTest 2.
    Root verify: common 65, user 86, catalog 285, cart 321 (2 skipped), payment 367, order 489.
  - Yerel uçtan uca (Docker user/catalog/cart/payment + `spring-boot:run` order; değer/id/token yazdırılmadı): kayıt 201 → sepete stokta
    kitap 200 → checkout 201 pending (Location = id, stockState/paymentId yok, ~750 ms) → 1 sn sonra GET 200 pending; Catalog rezervasyonu
    held; order satırı pending + held + paymentId dolu, 1 history; Payment kaydı succeeded (mock webhook) → ikinci checkout 409
    ORDER_PENDING_EXISTS (orderId = ilki); başka kullanıcı GET 404 (`instance` `/api/orders/:orderId`); token'sız 401. Order logunda
    yalnızca `Remote call ...` + `Checkout -> ORDER_PLACED/ORDER_PENDING_EXISTS` satırları, WARN/ERROR yok. e2e kullanıcısının siparişi
    PENDING KALDI ve o kullanıcıyı engelliyor (tüketici Adım 6, timeout Adım 8).
  - KARARLAR: (1) pending zaman aşımı 10 dk (Adım 8; rezervasyon TTL'i 15 dk'dan kısa, ödeme kurtarma ~40 sn'yi kapsar);
    (2) Payment Unknown → sipariş pending kalır, Adım 8 aynı idempotent istekle yeniden başlatır; (3) commit'te `AlreadyReleased` için
    ÖNERİ: V2 ile `stock_state 'lost'` (Adım 6 kararı).
  - Sapmalar: bilinmeyen alan 400 değil YOK SAYILIR (mevcut servislerin politikası; testli); country zorunlu (user'da TR varsayılanı);
    diğer OrderRuleViolation'lar 422 kendi koduyla; yeni failure kodları BOOK_NOT_AVAILABLE ve PAYMENT_REJECTED; Payment başka siparişin
    paymentId'sini dönerse `uk_orders_payment` → 409 CONFLICT (sözleşme ihlali kenarı, systemPatterns'te).

- Order Adım 6a — Payment sonucu RabbitMQ consumer + atomik Order olayları (COMMIT EDİLMEDİ; stok commit/release ve V2 YOK):
  - Keşfedilen upstream sözleşme: `PaymentSucceeded` / `PaymentFailed`, `eventVersion=1`, exchange `kitapsepeti.events`, routing
    `payment.succeeded` / `payment.failed`; payload ortak alanları `eventId`, `paymentId`, `orderId`, metin `amount`, `currency`,
    `occurredAt` (+ failed'da `failureCode`). AMQP: messageId=eventId, type=event_type, JSON/UTF-8, persistent, timestamp,
    `aggregateType=payment`, `aggregateId=paymentId`.
  - Topoloji: consumer sahibi Order; durable `order.payment-results`, topic exchange'e iki exact binding. Durable direct
    `kitapsepeti.dlx`, durable `order.payment-results.dlq`; ana kuyrukta DLX + `order.payment-results.dead` routing argümanı.
    Yeniden kullanılabilir saf kurucu `common.amqp.DeadLetterQueueTopology`; auto-config / component scan YOK, servis açıkça bean yapar.
  - Listener: prefetch 10, concurrency 1; stateless toplam 3 deneme (`maxRetries=2`), backoff 1s → 2s (4s üst sınır);
    retry tükenince requeue'suz reject → broker DLQ. `PoisonMessageException` tekrar edilmez: bozuk JSON, bilinmeyen type,
    sürüm ≠1, eksik/geçersiz alan, olmayan order, tutar veya para birimi uyuşmazlığı doğrudan DLQ. Güvenli tek satır sonuç logları;
    DLQ WARN, tutar/para uyuşmazlığı ERROR; id/tutar/gövde yok.
  - `PaymentSucceeded`: Order `FOR UPDATE`, tutar/para doğrulaması, farklı bağlı paymentId → PAYMENT_ID_CONFLICT ack; `markPaid` APPLIED
    → aynı READ_COMMITTED TX'te `OrderPaid` + `CartCheckedOut`; ALREADY → ack/yazma yok; failed sipariş → LATE_PAYMENT_SUCCESS ERROR + ack.
    Adım 6a'da paid sipariş `held` kalır.
  - `PaymentFailed`: Order `FOR UPDATE`, tutar/para doğrulaması, geçersiz failureCode → `PAYMENT_FAILED`; APPLIED → aynı TX'te
    yalnızca `OrderFailed`; ALREADY → ack; paid/farklı paymentId → ERROR + ack. Cart aktif kalır, CartCheckedOut YOK.
  - KARAR: failed olan HER sipariş (checkout Adım 4/5 yolları dahil) `OrderFailed` üretir. Bu nedenle
    `OrderTransactions.markFailed` yalnızca APPLIED'da aynı TX içinde outbox yazar; yarıda kesilip failed yapılamayan pending kalıntıda olay yok.
  - Outbound payload record sırası: `OrderPaid(eventId,eventVersion,orderId,userId,paymentId,totalAmount,currency,itemCount,occurredAt)`,
    `OrderFailed(eventId,eventVersion,orderId,userId,failureCode,occurredAt)`,
    `CartCheckedOut(eventId,eventVersion,cartId,userId,orderId,occurredAt)`; tümü v1, eventId'li, occurredAt=Order Clock zamanı,
    para metin. aggregate_type=`order`, aggregate_id=orderId. Routing: `order.paid`, `order.failed`, `cart.checked-out`.
  - KABUL EDİLEN Adım 6b kararı: Catalog commit `AlreadyReleased` dönerse yalnızca paid siparişte V2 `stock_state='lost'`;
    tekrar commit yok, ERROR, DB'den admin listesi. V1/commit çağrısı/StockSyncJob bu adımda eklenmedi.
  - Testler: common `DeadLetterQueueTopologyTest` (2); Order `PaymentResultListenerIT` (mutlu yol+gerçek relay, tekrar teslim,
    failed/fallback, 7 poison, geçici retry/kalıcı retry, iki conflict, log hijyeni, gerçek broker topolojisi/container ayarları)
    + payload record sıra/sürüm testleri; checkout failed yollarına OrderFailed ve interrupted pending yollarına olay yok beklentileri eklendi.
    Nihai sayılar: common 67, user 86, catalog 285, cart 321 (2 skipped), payment 367, order 560; kök `clean verify` yeşil.
    İlk kök denemede kapsam dışı Cart concurrency testi bir kez 500 üretti; tekil tekrar ve ikinci kök koşu yeşil.
  - Yerel E2E: yeni kullanıcı checkout 201 → paid; history 2, OrderPaid+CartCheckedOut yayımlanmış. Mock ret yolu ayrıca
    failed+OrderFailed verdi. Geçici kuyruk `order.paid`, `order.failed`, `cart.checked-out` mesajlarını gördü ve silindi; Payment
    results DLQ boş. Adım 4'ten olayı kayıp pending kalıntı hâlâ pending ve Adım 8'i bekliyor.
- **Order Adım 6b (YAPILDI):**
  - Flyway `V2__stock_state_lost.sql`: `stock_state` CHECK'e `'lost'` eklendi, `ck_orders_paid_state` güncellendi, `ck_orders_lost_paid` (`stock_state <> 'lost' OR status = 'paid'`) eklendi. V1'e dokunulmadı.
  - Domain: `StockState.LOST` eklendi; `markStockLost(clock)` (`PAID+HELD -> LOST: APPLIED`, `LOST: ALREADY_IN_STATE`, diğerleri `CONFLICTING_FINAL`); `markStockCommitted` ve `markStockReleased` LOST'ta `CONFLICTING_FINAL`.
  - Dispatcher & Coordinator: `StockDispatcher` (`TransactionPhase.AFTER_COMMIT`), `ThreadPoolTaskExecutor` (2-4 thread, queue 50, DiscardPolicy, dolunca WARN + drop; exception yok). Catalog commit/release çağrısı TX DIŞINDA (`StockCoordinator`), ardından `FOR UPDATE` ile durum güncellenir.
  - Sonuç eşleme: Commit: `Committed` -> committed; `AlreadyReleased` (409) / `Rejected (404 RESOURCE_NOT_FOUND)` -> lost + ERROR STOCK_COMMIT_LOST; diğer Rejected (401 vb.) -> held + ERROR; NotPerformed / Unknown -> held + WARN. Release: `Released` -> released; `AlreadyCommitted` -> ERROR, değişiklik yok; diğerleri WARN.
  - `StockSyncJob`: `@Scheduled(fixedDelayString = "${app.stock-sync.interval:30s}")`, min-age 10s, batch 50. `(stock_state, updated_at)` bileşik indeksi doğrulanmıştır. Devre kesici açıkken tur erken biter. Tur sonunda iş yapıldıysa tek INFO satırı (id/tutar yok). Tek instance varsayımı geçerlidir.
  - Admin listesi şablonu: `SELECT id, user_id, status, stock_state, total_amount, currency, created_at, updated_at FROM orders WHERE stock_state = 'lost' ORDER BY updated_at DESC;`.
  - Testler: `OrderTransitionsTest` (93), `OrderSchemaConstraintsTest` (117), `StockDispatcherIT` (9), `StockSyncJobIT` (8), `StartupLogHygieneTest` (V1+V2 2 migration doğrulama). Monorepo testleri tam yeşil: common 67, user 86, catalog 285, cart 321 (2 skipped), payment 367, order 599.
  - Yerel E2E: Docker servisleri + yerelde order-service açılışında V2 başarıyla uygulandı; 6a kalıntısı paid+held rezervasyon süresi dolduğu için Catalog 409 döndü -> lost + ERROR STOCK_COMMIT_LOST; failed+held -> released oldu; yeni kullanıcıyla checkout -> paid -> saniyeler içinde committed ve Catalog stoku düştü; ret yolu (.99 kuralı) mevcut veriyle kurulamadığı için atlandı; lost sorgusu 1 sonucunu verdi.
- **Cart Adım 7 = Order planı Adım 7 (YAPILDI, COMMIT EDİLMEDİ): CartCheckedOut tüketicisi + CartConcurrencyTest 500 düzeltmesi.**
  - RabbitMQ: cart'a `spring-boot-starter-amqp`; env adları diğerleriyle aynı (`RABBITMQ_HOST/PORT/USER/PASSWORD`), CachingConnectionFactory
    WARN. Exchange tanımı common `amqp.EventsExchange.create(name)` (durable topic; `OutboxConfiguration` de artık bunu kullanır,
    davranış aynı); cart `config/EventsExchangeConfig` + `app.events.exchange`. Compose cart-service: rabbitmq `service_healthy` +
    RABBITMQ env, env_file yok. Readiness DEĞİŞMEDİ (readinessState + db, tüm servislerle aynı); `rabbit` göstergesi kök health'te
    (broker kapalı → kök DOWN, readiness/liveness UP; `RabbitDownReadinessTest`).
  - Topoloji (`config/CartCheckoutsConsumerConfig`, `DeadLetterQueueTopology`): durable `cart.checkouts` ← `kitapsepeti.events`
    `cart.checked-out`; DLX `kitapsepeti.dlx` → `cart.checkouts.dlq` (key `cart.checkouts.dead`). Listener Order 6a kopyası: prefetch 10,
    tek consumer, toplam 3 deneme (1s/2s, 4s tavan), poison retry'sız DLQ. Test profilinde consumer kapalı (`app.cart-checkouts.enabled=false`).
  - FARK (Order'dan): container error handler'ı fırlatmıyor (no-op). Fırlatınca Spring AMQP her DLQ mesajında
    "error handler threw an exception" ERROR + stack trace yazıyor; container özgün istisnayı zaten yeniden fırlattığı için reject
    (requeue yok) aynı. Order'da da Adım 8'de aynı no-op'a geçildi.
  - İşleme (`CartTransactions.checkOut`, READ_COMMITTED, `findByIdForUpdate(cartId)`): sepet yok → poison CART_NOT_FOUND (WARN DLQ);
    userId farklı → poison CART_OWNER_MISMATCH (ERROR DLQ); active → `checkout(clock)` (updated_at = Clock) INFO CHECKED_OUT;
    checked_out → ack DEBUG; abandoned → ack WARN, durum değişmez. Satır silinmez; sonraki ekleme yeni aktif sepet açar
    (`uk_carts_active_user` yalnız active'te değerli). Migration GEREKMEDİ. Log: mesaj başına tek satır (type, sonuç, durationMs), id yok.
  - v1 SINIRI: sipariş pending iken sepete eklenen ürün, sepet kapanınca gider (olayda satır listesi yok).
  - CartConcurrencyTest 500 kök nedeni: eşzamanlı "ilk sepet" INSERT'lerinde InnoDB deadlock (1213; unique index duplicate kontrolü +
    delete-marked kayıtlar → supremum'da next-key S kilidi, ardından insert-intention X çakışması) → `CannotAcquireLockException`
    (`ConcurrencyFailureException`), `CartService.addItem` yalnız `uk_carts_active_user` ihlalini yeniden deniyordu → 500. Düzeltme:
    ilk sepet yarışında SQLState zincirinde 1213 de bir kez yeni TX'te yeniden denenir (1205 lock wait timeout HARİÇ; ikinci hata yukarı).
    Önce 900 senaryoda 17 hata, sonra 0. API/hata kodları aynı. 30 ayrı koşu 30/30.
  - Testler: `CartCheckedOutListenerIT` (15: mutlu yol + GET boş, tekrar teslim, kapanış sonrası ekleme, 6 poison, geçici→başarı,
    kalıcı→DLQ, abandoned, broker topolojisi, log hijyeni), `CartCheckedOutMessageParserTest` (15), `CartCheckedOutContractTest` (3: Order'ın
    `CartCheckedOutEvent` kaynağı test anında `javax.tools.JavaCompiler` ile derlenir, Boot JsonMapper ile serileştirilip Cart parser'ına
    verilir; routing key `EventRoutingKeys.java` metninden), `RabbitDownReadinessTest`, CartLockingTest +1, CartServiceTest +3,
    common `EventsExchangeTest` (2). Kök `clean verify`: common 69, user 86, catalog 285, cart 359 (2 skipped), payment 367, order 599.
  - Testcontainers reuse cart'ta açık (`withReuse(true)`, MySQL DB adı `cart_test`); hız kuralları techContext'te.
  - Docker: cart-service yeniden derlendi, healthy; `cart.checkouts` (1 consumer) / `cart.checkouts.dlq` / binding'ler doğru. Hafif e2e
    (yeni kullanıcı, yerel order-service): checkout 201 → paid (<1 sn) → GET /api/cart boş (~2 sn), DLQ boş; order-service durduruldu.
  - ADMIN KEŞFİ (yalnızca keşif, atama yapılmadı): user-service `Role {USER, ADMIN}`, `users.role` 'USER' varsayılan
    (`ck_users_role`), JWT `role` claim'i = enum adı; common `JwtRoleConverters` → `ROLE_<role>`; catalog `/api/admin/**`
    `hasRole("ADMIN")`. Admin atayan seed/env/uç YOK; tek yol DB'de `role='ADMIN'` güncellemesi (B3, kullanıcı onayıyla). Rol token'a
    giriş/refresh'te yazılır → değişiklikten sonra yeniden giriş gerekir.
- **Order Adım 8 (YAPILDI, COMMIT EDİLMEDİ): PendingReconciliationJob + geç ödeme kaydı + temizlik.**
  - Temizlik: (a) `PaymentResultsConsumerConfig` error handler'ı no-op (Cart kalıbı) → DLQ'da ERROR + stack trace yok, reject/DLX
    aynı (test: zehirli mesajda `"\tat "`/`Caused by` yok). (b) Order Testcontainers reuse: MySQL `withDatabaseName("order_test")
    .withReuse(true)`, RabbitMQ `withLabel("com.kitapsepeti.test-module","order").withReuse(true)` (Cart'ın aynı ayarlı Rabbit'iyle
    reuse hash'i çakışmasın diye etiket; Cart koduna dokunulmadı). Bağlam sayısı 7 (değişmedi); tam koşuda 6 MySQL + 3 Rabbit
    başlatması reuse, yalnızca StartupLogHygiene'in kendi MySQL'i (kasıtlı) yeni. Order modül süresi 2:38 → 1:35 (44 test fazlasıyla).
  - Flyway `V3__late_payment.sql`: `orders.late_payment_at DATETIME(6) NULL` (failure_code'dan sonra) + `ck_orders_late_payment_failed`
    (`late_payment_at IS NULL OR status = 'failed'`). V1/V2'ye dokunulmadı.
  - Domain `Order.recordLatePayment(paymentId, clock)`: yalnız FAILED; null ise set (+ paymentId null ise bağla) → APPLIED; zaten set →
    ALREADY_IN_STATE; FAILED değil → CONFLICTING_FINAL. Başka ödeme bağlıysa yine kaydeder, paymentId DEĞİŞMEZ.
  - `OrderTransactions`: ortak özel `succeed(order, paymentId)` (tüketici `applyPaymentSucceeded` + görev `reconcilePaymentSucceeded`;
    markPaid + OrderPaid + CartCheckedOut + commit dispatch; failed'da recordLatePayment) ve `fail(...)` (`applyPaymentFailed` +
    `reconcilePaymentFailed`; markFailed + OrderFailed + release dispatch). Yeni `failPendingAndReleaseStock(orderId, expectedStock,
    code)`: kilit altında pending ama stok seçimdekinden farklıysa dokunmaz. Yeni çıktı `LATE_PAYMENT_ID_CONFLICT`.
  - Tüketici: failed siparişe PaymentSucceeded → late_payment_at + ERROR `LATE_PAYMENT_SUCCESS` (+ farklı ödeme ise ERROR
    `PAYMENT_ID_CONFLICT`), ack, outbox yok; tekrar → ALREADY_IN_STATE, tek kayıt.
  - Payment istemcisi: `PaymentResponse.failureCode` (sözleşmede required+nullable → DTO `required = true`) ve
    `PaymentInitiationResult.Initiated.failureCode` (2 argümanlı kurucu korunur). Ret kodu doğrulaması `OrderReasons.paymentFailureCode`
    (geçersiz/yok → PAYMENT_FAILED; tüketici ve görev ortak).
  - `PendingReconciliationJob` (`app.pending-reconcile.*`: enabled true, interval 30s, initial-delay 15s, min-age 60s, expire-after 10m,
    batch 50; test profilinde kapalı, IT'de elle kurulur). Karar tablosu systemPatterns "Bekleyen sipariş uzlaştırma"da.
    Seçim `findPendingReconcileCandidates` (status=pending AND created_at < now−min-age, created_at sırası, LIMIT; `ix_orders_status_created`,
    filesort yok — EXPLAIN testi). Tek instance varsayımı. Tur sonu INFO `Pending reconcile round completed: processed=N, counts=[...]`
    yalnız WAITING dışı bir sonuç varsa; id/tutar yok.
  - Testler: `PendingReconciliationJobIT` (22; `PaymentResultListenerIT` ile birebir aynı `@TestPropertySource` → bağlam paylaşılır,
    gerçek tüketici açık: yarış ve "expire sonrası geç ödeme" uçtan uca), `PaymentResultListenerIT` 19 (+geç ödeme/tekrar, farklı ödeme,
    zehirli mesajda stack trace yok), `OrderTransitionsTest` 106, `OrderSchemaConstraintsTest` 122 (V3 kabul/ret), `PaymentGatewayTest` 19.
    `.\mvnw -pl order-service -am verify`: common 69, order 643 (önceki 599).
  - Yerel E2E: açılışta V3 uygulandı; Adım 4'ten kalan (pending+held+ödemeli, >10 dk) sipariş ilk uzlaştırma turunda Payment'tan
    "succeeded" alıp paid oldu, CartCheckedOut ile o kullanıcının sepeti kapandı (engel kalktı). Stok commit'i StockSyncJob turunu
    beklemeden ödeme geçişinin dispatcher'ından hemen denendi: rezervasyon süresi dolmuştu → AlreadyReleased → lost + ERROR
    STOCK_COMMIT_LOST. Başka pending kalıntı yoktu. Yeni checkout → paid (~2 sn) → committed → sepet kapandı. Order log'unda stack trace yok.
  - İade listesi (admin sorgu şablonu, v1'de iade mekanizması YOK, yalnızca kayıt):
    `SELECT id, user_id, payment_id, total_amount, currency, failure_code, late_payment_at FROM orders WHERE status = 'failed' AND late_payment_at IS NOT NULL ORDER BY late_payment_at DESC;`

## Sonraki adımlar
- PROJE KARARI (Ekim 2026, UI paralel): UI (Angular 13) Order ile PARALEL başlıyor — ayrı agent, ayrı worktree
  (`..\kitapSepeti-ui`, branch `ui`), yalnızca `frontend/` + `memory-bank/frontend.md` + `.cursor/rules/frontend-angular13.mdc`.
  Plan: kitapSepetiPlanlama `ui.md` + `docs/ui-roadmap.md`. Backend agent'ına kurallar: (1) Docker'da yalnızca kendi servisini
  rebuild et (`docker compose up -d --build order-service`), diğer servisleri durdurma, `down -v` yok (UI aynı container'ları
  kullanıyor); (2) Order sözleşmesi (CheckoutRequest, OrderResponse, hata kodları) Adım 4 sonunda dondurulur (UI-7 buna göre).
  UI için küçük backend işleri: B1 catalog `GET /api/books?q=` başlık araması (OpenAPI + drift testi), B2 Docker'da katalog örnek
  verisi (compose'da catalog varsayılan profilde, seed yok), B3 bir kullanıcıyı ADMIN yapmak (SQL).
- PROJE KARARI (Ekim 2026): sıra Payment → Order → Gateway → UI → (vakit kalırsa) Notifications. Notifications v1 yalnızca uygulama
  içi bildirim (OrderPaid/OrderFailed; e-posta, tercih, şablon yok). Order fazında `order-paid.md` ve `order-failed.md` olay
  sözleşmeleri yine yazılacak.
- PROJE KARARLARI — Order (Ekim 2026):
  - Checkout `201` + `pending` döner; UI sonucu polling ile izler (GET {id}).
  - Kullanıcı başına tek `pending` sipariş: generated kolon + UNIQUE; ikincisi `409 ORDER_PENDING_EXISTS`.
  - Teslimat adresi checkout gövdesinden alınır ve siparişe snapshot olarak yazılır (user-service'e çağrı yok).
  - Sepet `CartCheckedOut` olayıyla kapanır (Cart tüketicisi Adım 7).
  - Circuit breaker Order Adım 3'te tüm Feign istemcilerine (cart, catalog, payment) — 3a'da Order tarafı, 3b'de Cart→Catalog yapıldı
    (kurulum `common.resilience`).
  - v1 kuponsuz.
  - Önce kayıt sonra dış çağrı: sipariş satırı kendi TX'inde yazılır, sonra rezervasyon/ödeme çağrıları.
  - Stok commit/release Catalog internal HTTP ile (`/internal/stock/reservations/{orderId}/commit|release`).
- **Order Adım 9 (YAPILDI, COMMIT EDİLMEDİ): GET /api/orders kullanıcının kendi siparişleri sayfalı özeti.**
  - Uç: `GET /api/orders?page=0&size=20` (Bearer). Yalnızca token sahibinin siparişleri (`@CurrentUserId`).
  - Sayfalama sözleşmesi: Catalog `BookSearchRequest` / `PageResponse` ile birebir aynı: `page >= 0` (varsayılan 0), `size 1..50` (varsayılan 20). Geçersiz değerlerde aynı 400 `VALIDATION_FAILED` (`errors[{field, message}]`). Sıralama sabit: `created_at DESC, id DESC`; sort ve filtre parametresi yok.
  - Yanıt zarfı: `PageResponse<OrderSummaryResponse>` (`items`, `page`, `size`, `totalElements`, `totalPages`). Aralık dışı sayfada boş `items`, doğru toplamlar.
  - Öğe DTO'su: `OrderSummaryResponse(id, status, failureCode, currency, totalAmount, itemCount, createdAt, updatedAt)`. Para alanı detay uçla aynı (scale 2). Yasak alanlar (`address`, `items`, `stockState`, `paymentId`, `latePaymentAt`, `userId`, `cartId`, `subtotal`, `discountAmount`, `history`) JSON'da yok.
  - Sorgu mimarisi: `OrderRepository.findSummariesByUserId` JPQL constructor projection + skaler alt sorgu `(select count(i) from OrderItem i where i.order = o)`. Tek SQL'de `itemCount` çekilir, N+1 yok. MySQL 8.4 `ix_orders_user_created (user_id, created_at, id)` bileşik indeksini backward index scan ile kullanır, filesort yok. Sayım sorgusu da `user_id` ile sınırlı ve aynı indeksi kullanır. 1 ve 20 öğeli sayfalarda SQL sorgu sayısı aynı (2 sorgu).
  - Güvenlik ve yol maskeleme: `RequestPathMasker`'da yeni yol değişkeni yok (`/api/orders`), coverage testi yeşil. `SecurityRulesTest`'te daha önce henüz olmayan yol olarak varsayılan `/api/orders` beklentileri güncellendi (artık geçerli token'la 200).
  - Testler: `OrderListTest` (14 test: boş liste 200/sayfa bilgileri, createdAt DESC + id DESC sıralama, sayfa sınırları/son sayfa/aralık dışı sayfa, kullanıcı izolasyonu ve totalElements, geçersiz page/size parametreleri 400, tokensız 401, öğe alanları/yasak alanlar, 1 ve 20 öğeli sayfalarda 2 SQL sorgusu / N+1 yokluğu, EXPLAIN indeks ve filesortsüzlük doğrulaması), `SecurityRulesTest` (26 test). `.\mvnw -pl order-service -am verify`: common 69, order 657 (önceki 643, +14).
- Order planı: 0a common sertleştirme (YAPILDI) → 0b outbox → common (YAPILDI, push'landı) → 1 modül/db (YAPILDI, push'landı) → 2 domain (YAPILDI) → 3a Order istemcileri + CB (YAPILDI) → 3b Cart→Catalog CB (YAPILDI) → 4 checkout mutlu yol +
  GET {id} (YAPILDI) → 5 hata yolları/telafi (YAPILDI) → 6a Payment sonucu consumer+Order olayları (YAPILDI) → 6b stok
  commit/release+StockSyncJob+V2 lost (YAPILDI) → 7 Cart CartCheckedOut tüketicisi (YAPILDI) → 8 timeout görevi
  (PendingReconciliationJob + late_payment, YAPILDI) → 9 liste (YAPILDI) → 10 OpenAPI/olay belgeleri → 11 Docker.
- (KAPANDI, Cart Adım 7) CartConcurrencyTest 500 flake'i: ilk sepet INSERT deadlock'u (1213) artık bir kez yeniden deneniyor.
- (KAPANDI, Order Adım 8) Order consumer DLQ gürültüsü: error handler no-op, DLQ'da stack trace yok.
- Veri Değiştirme Kuralı: Catalog ve User verisi YALNIZCA ilgili servisin API'siyle değiştirilir; doğrudan SQL ile yazma KESİNLİKLE YOKTUR (root yalnızca okuma). Admin token yoksa DUR ve sor. Raporda id, başlık, token, tutar ASLA YAZILMAZ.
- order-service eklenirken: `RequestPathMasker` bean'i (`/api/orders/{orderId}` vb.) ve `InternalAuthConfig` (gerekirse) — common
  politika aynen geçerli.
- Payment artık işleri: (1) iyzico sandbox sağlayıcısı; (2) aynı siparişe eşzamanlı ilk isteklerde birden fazla sağlayıcı çağrısı
  (gerçek sağlayıcıda idempotency anahtarı = paymentId); (3) outbox yayın hatası WARN'ındaki eventId (artık common `OutboxRelay`:
  `id=…, eventType=…`) kaldırılmalı/maskelenmeli.
- Order istemcisi `.env` `ORDER_INTERNAL_API_KEY` ile `X-Internal-Api-Key` gönderir (payment özeti `PAYMENT_INTERNAL_KEY_ORDER_SHA256`);
  compose'da `http://payment-service:8087`, istemci `docs/api/payment-service.openapi.json`'dan.
- Cart ertelenenler: (1) CartCheckedOut tüketimi YAPILDI (Cart Adım 7; checkout ve yeni sepet ayrı TX'lerde, flush tuzağına girmez);
  (2) Catalog OpenAPI'de nullable alanları `types = {"x","null"}` ile işaretleme (cart'taki gibi). (Yol maskeleme + özet
  politikası Order Adım 0a'da common'a taşındı.)
  Order'ın sepet istemcisi `docs/api/cart-service.openapi.json`'dan (internal snapshot dahil); compose'da `http://cart-service:8083`.
- Gateway fazı: `/v3/api-docs` + Swagger UI dört serviste (user, catalog, cart, payment) permitAll; Gateway'de dışarıya kapatılacak (ya da `SPRINGDOC_ENABLED=false`).
- order-service (rezervasyon istemcisi; fiyat anlık görüntüsü kendisinde; `RESERVATION_RELEASED` → ödeme iadesi telafisi; süre dolumu
  olayı yok, GET ile sorgulanır; istemci `docs/api/catalog-service.openapi.json`'dan). Compose'a eklenirken catalog'a
  `http://catalog-service:8082` ve `.env` `ORDER_INTERNAL_API_KEY` ile bağlanır.
- Backlog: `BookUpserted.priceAmount` sayıya geçecekse outbox payload kolonunu metin tipine çeviren yeni migration gerekir.
- Backlog: yayınevi/yazar/kategori yeniden adlandırılınca yayındaki kitaplar için olay ÜRETİLMİYOR; Search servisi gelince
  yeniden indeksleme (ya da bu değişikliklerde etkilenen kitaplar için BookUpserted) gerekecek.
- Backlog: admin PATCH'te bilinmeyen alanlar (stok, status) sessizce yok sayılıyor; ileride 400 düşünülebilir.
- Logout, e-posta/parola değiştirme, consumer servisler, CORS.
- Açık konular: DataSourceHealthIndicator stack trace gürültüsü; CI pipeline yok (drift testi yalnızca yerel `mvnw test`'te).
- Açık konu (common outbox; user, catalog, payment): yayınlanmış satırların temizliği (retention) yok; bilinmeyen event_type
  kuyruğun başını tıkar (her turda WARN, sonraki satırlar bekler). Bkz. systemPatterns "Ortak outbox" bilinen sorunlar.
- Açık konu (catalog): tıkanan süre dolumu siparişleri kuyruğun başını tıkayabilir (batch dolarsa), outbox'taki tanınmayan
  event_type sorunuyla birlikte çözülecek.
- Açık konu (catalog OpenAPI): `/v3/api-docs` her profilde açık (user-service ile aynı) → internal uçların şekli de herkese görünür
  (sır yok); prod'da `SPRINGDOC_ENABLED=false` düşünülmeli.
- Yeni servisler eklendikçe kök POM kontrol listesini uygula (bkz. systemPatterns.md; [5] common, [6] Dockerfile).
- Order Adım 3a–5 tamamlandı; Adım 6a Payment sonucu consumer+Order olayları tamamlandı (commit edilmedi). Sıradaki Adım 6b:
  stok commit/release, StockSyncJob ve V2 `lost`. Adım 5 telafisi systemPatterns "Order istemcileri" NotPerformed/Unknown kuralına
  dayanır (Unknown → idempotent tekrar ya da GET ile netleştirme).
- KARAR (Order Adım 6b): commit'te `AlreadyReleased` (rezervasyon süresi doldu, 15m + görev ≤30 sn) → yalnız paid ile V2
  `stock_state 'lost'`; tekrar commit yok, ERROR, DB'den admin listesi. Adım 8 pending zaman aşımı 10 dk.
- Backlog (catalog OpenAPI): lookup/rezervasyon yanıtındaki `status` kodda "mixed" üretebilir (satır durumları farklıysa,
  `StockReservationTransactions`), OpenAPI enum'unda YOK (Order → Unknown).
- Order Adım 2 YAPILDI (commit `51feb91`, push edildi). Adım 4'te `RequestPathMasker` deseni `/api/orders/{orderId}`
  + sipariş hata kodları/DB kısıtı eşlemesi (`GlobalExceptionHandler`: `OrderRuleViolation` → 422 kod adıyla, `uk_orders_pending_user`
  → 409 ORDER_PENDING_EXISTS).
- KARAR (Order): tamamı ücretsiz sepet → `422 ORDER_TOTAL_ZERO`, sipariş yazılmadan önce (Adım 4/5).
