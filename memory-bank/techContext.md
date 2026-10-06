# Tech Context

- Java 21 (Eclipse Adoptium), Maven 3.9.16 (wrapper ile)
- Spring Boot 4.1.1
- user-service: Spring Web MVC (port 8081), Spring Data JPA, Flyway (MySQL), Spring Security, Validation,
  Lombok. Spring Modulith tamamen kaldırıldı (bağımlılıklar + kök POM'daki BOM); olay yayını için
  kendi `outbox` tablosu kullanılacak.
- user-service şeması (V1__create_user_tables.sql): `users`, `addresses`, `refresh_tokens`, `outbox`.
  UUID = BINARY(16) (uygulama üretir), zaman = DATETIME(6) UTC, collation utf8mb4_0900_ai_ci
  (e-posta UNIQUE büyük/küçük harf duyarsız). Kullanıcı başına tek default adres:
  `addresses.default_owner` VIRTUAL generated kolon + UNIQUE (STORED olamaz: MySQL, STORED generated
  kolonun kaynak kolonundaki FK'da ON DELETE CASCADE'e izin vermiyor — ERROR 1215).
- user-service yapılandırması: `src/main/resources/application.yml`; DB bilgileri `spring.config.import`
  ile kökteki `.env`'den okunur (`USER_DB_USER`, `USER_DB_PASSWORD`, opsiyonel `USER_DB_HOST`/`USER_DB_PORT`).
  `ddl-auto: validate`, şema Flyway'de (`classpath:db/migration`).
- Çalıştırma: kökten `.\mvnw.cmd -pl user-service spring-boot:run` (MySQL container healthy olmalı).
- Container ile çalıştırma: `docker compose build user-service` + `docker compose up -d` (image
  `kitapsepeti/user-service:local`, port 8081 — yerel `spring-boot:run` ile aynı anda çalışamaz; biri durdurulmalı).
  Health: `/actuator/health/liveness`, `/actuator/health/readiness` (public, yalnızca status).
- Adminer (profil `tools`): aç `docker compose --profile tools up -d adminer`, kapat
  `docker compose --profile tools stop adminer`. Adres http://localhost:8090; giriş: System MySQL, Server `mysql`
  (localhost değil), Username `user_svc`, Password `.env`'deki `USER_DB_PASSWORD`, Database `user_db`. Salt okunur kullanım.
- Terminalden DB: `docker compose exec mysql mysql -uuser_svc -p user_db` (parola istemi yazılanı göstermez).
- Docker Desktop 29.x, Compose v5, buildx 0.37. Base image'lar `eclipse-temurin:21-jdk` / `21-jre` (Ubuntu 26.04).
  Dockerfile'lar `COPY --parents` kullanır (frontend 1.20+ GA; `# syntax=docker/dockerfile:1` yeterli). maven-dependency-plugin
  3.10.0 `go-offline` reaktör modülünü (common) uzaktan çözmeye çalışmaz.
- payment-service: port 8087, şema `payment_db`, kullanıcı `.env` `PAYMENT_DB_USER/PASSWORD` (yerelde `payment_svc`;
  `infra/mysql/init/30-payment-db.sh`; compose mysql env'inde `${VAR:?}` → .env'de yoksa HİÇBİR compose komutu çalışmaz). Çalıştırma
  `.\mvnw.cmd -pl payment-service spring-boot:run` (önce `.\mvnw.cmd -pl common -am install -DskipTests`), testler
  `.\mvnw.cmd -pl payment-service -am test`. Bağımlılıklar webmvc, data-jpa, flyway(-mysql), validation, actuator, security (Adım 3),
  amqp (Adım 4), springdoc (Adım 7), mysql, common; openfeign YOK (testle kilitli). Container (Adım 8): `docker compose build
  payment-service` + `docker compose up -d` (imaj `kitapsepeti/payment-service:local`, 604 MB, kullanıcı `app` 10001; yerel
  `spring-boot:run` ile aynı anda çalışamaz). Health `/actuator/health/{liveness,readiness}`; ayrıntı `docs/docker.md`. `spring-boot:run` DB,
  RabbitMQ (`RABBITMQ_HOST/PORT/USER/PASSWORD`, catalog ile aynı adlar) ve internal anahtar özetini `spring.config.import` ile
  `.env`'den kendisi okur. Testler RabbitMQ'yu `RabbitTestcontainersConfiguration` (rabbitmq:4-management) ile alır;
  outbox worker testte kapalı (`app.outbox.enabled: false`), relay testleri açar.
- order-service: port 8088, şema `order_db`, kullanıcı `.env` `ORDER_DB_USER/PASSWORD` (`infra/mysql/init/40-order-db.sh`; parola
  yalnızca harf/rakam; compose mysql env'inde `${VAR:?}`). Çalıştırma `.\mvnw.cmd -pl order-service spring-boot:run` (önce
  `.\mvnw.cmd -pl common -am install -DskipTests`); DB/RabbitMQ/JWKS ayarlarını `spring.config.import` ile `.env`'den kendisi okur
  (`USER_SERVICE_JWKS_URI` yoksa `http://localhost:8081/.well-known/jwks.json`). Testler `.\mvnw.cmd -pl order-service -am test`
  (MySQL + RabbitMQ Testcontainers). Bağımlılıklar: payment ile aynı + security-oauth2-resource-server + (Adım 3a)
  spring-cloud-starter-openfeign, feign-java11 (JDK HttpClient), resilience4j-circuitbreaker (Adım 4'ten beri; Spring Cloud CB
  starter'ı kaldırıldı, cart ile aynı) (Spring Cloud 2025.1.3 BOM: OpenFeign 5.0.3, feign 13.6.1, resilience4j 2.3.0); test
  wiremock-standalone 3.13.1 (sürüm servis pom'unda). Uçlar (Adım 4): `POST /api/orders/checkout`, `GET /api/orders/{orderId}`.
  Adım 5 ile gelenler: satır içi stok release (`releaseStock`), yarıda kesilme (interrupted checkout a-e) yönetimi,
  `OrderReasons.CHECKOUT_INTERRUPTED` ("CHECKOUT_INTERRUPTED"), `OrderErrorCode.ORDER_UNAVAILABLE` (503),
  `OrderErrorCode.CHECKOUT_INTERRUPTED` (503).
  Adım 6a: ilk inbound Rabbit consumer; `order.payment-results` (payment.succeeded/failed), `kitapsepeti.dlx` direct,
  `order.payment-results.dlq`. Prefetch 10/concurrency 1; stateless toplam 3 deneme, 1s→2s (4s tavan), poison retry yok.
  `common.amqp.DeadLetterQueueTopology` saf Declarables kurucusu (auto-config yok). Order outbox event/routing:
  OrderPaid→order.paid, OrderFailed→order.failed, CartCheckedOut→cart.checked-out. Test profilinde consumer ve relay varsayılan kapalı;
  `PaymentResultListenerIT` ayrı bağlamda ikisini gerçek RabbitMQ Testcontainer ile açar.
  Yerel uçtan uca: Docker'da user/catalog/cart/payment + `spring-boot:run` order (compose host portlarına localhost'tan bağlanır);
  kontrol script'i `.env`'yi Ordinal okur, değer yazdırmaz; DB sorgusu `$env:MYSQL_PWD` + `docker compose exec -T -e MYSQL_PWD mysql
  mysql -u<kullanıcı> <db> -N` (SQL stdin'den).
  Env: `ORDER_INTERNAL_API_KEY` (zorunlu; yok/boşsa açılmaz), `ORDER_CART_URL`/`ORDER_CATALOG_URL`/`ORDER_PAYMENT_URL` (varsayılan
  localhost:8083/8082/8087; `.env.example`'da henüz yok). Testlerde sahte anahtar application-test.yml'de. Compose'da YOK (Adım 11).
- payment internal anahtarı: `.env` `PAYMENT_INTERNAL_KEY_ORDER_SHA256` (Order'ın ham anahtarı `ORDER_INTERNAL_API_KEY`'in SHA-256 hex
  özeti; `.env.example`'da boş). Yok/boş/bozuk → payment-service açılmaz. Testler JVM'de üretilen anahtarı `InternalTestKeys.register`
  (DynamicPropertySource) ile verir.
- payment mock webhook secret'ı (Adım 5): `.env` `PAYMENT_MOCK_WEBHOOK_SECRET` (rastgele, ≥32 karakter; kullanıcı 43 karakter
  base64url girdi; `.env.example`'da boş) → `app.payment.mock.webhook-secret`. Yok/boş/<32 → payment-service açılmaz. Yalnızca
  payment-service kullanır (Adım 6'daki mock dispatcher da aynı secret'la imzalar). Testler `WebhookTestSecrets.register` ile
  çalıştırma başına rastgele secret verir. `app.payment.webhook.tolerance` (5m) ve `max-body-bytes` (65536) application.yml'de.
- payment mock gönderim ayarları (Adım 6, `app.payment.mock.*`): `delay` 500ms, `webhook-url` (yoksa uygulamanın portu),
  `dispatch.enabled` true / `dispatch.queue-capacity` 100, `recovery.enabled` true / `interval` 30s / `min-age` 10s / `batch-size` 50
  (1–1000). Test profilinde dispatch ve recovery kapalı. Yerelde geçici RabbitMQ kuyruğu: konteyner içindeki `rabbitmqadmin` (2.35,
  HTTP API) `RABBITMQADMIN_USERNAME/PASSWORD` ← konteynerin `RABBITMQ_DEFAULT_USER/PASS` env'i (değer yazdırılmaz); RabbitMQ 4'te
  transient non-exclusive classic kuyruk yasak → `--durable true`, iş bitince `delete queue`.
- payment `app.payment.*` (`config/PaymentProperties`, @Validated): `provider` (varsayılan mock; v1'de yalnızca mock açılır),
  `mock.fail-cents` 0–99 (varsayılan 99). DİKKAT: `app.payment.provider=` (boş) hata vermez, Binder boş değeri null yapıp
  `@DefaultValue("mock")`'a düşer (testle sabitlendi). Bağlam testleri `ApplicationContextRunner().withUserConfiguration(PaymentConfig.class)`.
- Compose host portları YALNIZCA `127.0.0.1` (8081, 8082, 8083, 3306, 5672, 15672, adminer 8090): LAN'dan erişilmez, localhost'tan
  erişilir; container'lar arası servis adıyla erişim etkilenmez.
- Spring Cloud: release train `2025.1.3` (Oakwood; Boot 4.0.x/4.1.x, 4.1 desteği 2025.1.2'den itibaren), kök POM BOM import.
  Boot yükseltilirken spring.io/projects/spring-cloud uyumluluk tablosu yeniden kontrol edilir; milestone/RC ve milestone repo kullanılmaz.
  Kullananlar: cart-service (`spring-cloud-starter-openfeign` 5.0.3 + Adım 3b'den `resilience4j-circuitbreaker` 2.3.0, Spring Cloud CB
  starter'ı YOK), order-service (OpenFeign + `resilience4j-circuitbreaker`; Spring Cloud CB starter'ı Adım 4'te kaldırıldı), common
  (`resilience4j-circuitbreaker` optional; sürüm BOM'dan).
- cart-service: port 8083, şema `cart_db`, kullanıcı `.env` `CART_DB_USER/PASSWORD` (`infra/mysql/init/20-cart-db.sh`). Çalıştırma
  `.\mvnw.cmd -pl cart-service spring-boot:run` (common `~/.m2`'de değilse önce `.\mvnw.cmd -pl common -am install -DskipTests`),
  testler `.\mvnw.cmd -pl cart-service -am test`. Container: `docker compose build cart-service` + `docker compose up -d`
  (imaj `kitapsepeti/cart-service:local`, 618 MB disk / 189 MB içerik; uid 10001 `app`; yerel `spring-boot:run` ile aynı anda
  çalışamaz — ikisi de 8083). Compose env: `CART_DB_USER/PASSWORD`, `CART_INTERNAL_KEY_ORDER_SHA256` zorunlu (`${VAR:?}`), Catalog adresi
  `CATALOG_BASE_URL` (varsayılan `http://localhost:8082`; compose'da `http://catalog-service:8082`). Catalog circuit breaker ayarı
  `app.circuit-breaker.*` (application.yml; env yok). Gerçek Catalog'a karşı test:
  `.\mvnw.cmd -pl cart-service test "-Dtest=CatalogLiveTest" "-Dcatalog.live=true"` (catalog seed'li olmalı). Hikari `connection-init-sql` ile
  `innodb_lock_wait_timeout = 5` (oturum; GLOBAL 50). Uçlar: `GET /api/cart`, `POST /api/cart/items` (USER token).
  Cart Adım 7'den beri RabbitMQ consumer'ı (`spring-boot-starter-amqp`; `RABBITMQ_HOST/PORT/USER/PASSWORD`, compose'da rabbitmq
  `service_healthy`). Test profilinde consumer kapalı; `CartCheckedOutListenerIT` ayrı bağlamda açar. Testler RabbitMQ'yu
  `RabbitTestcontainersConfiguration` (rabbitmq:4-management) ile alır (ApiTestSupport + kök health UP bekleyen iki Catalog testi).
  Eşzamanlılık tekrarı: `.\mvnw.cmd -q -pl cart-service test "-Dtest=CartConcurrencyTest"` (common kurulu olmalı).
  Uçtan uca denemede seed 402 yayında ama stokta değil (409 BOOK_NOT_AVAILABLE); stokta yayındaki kitaplar 401, 404–411.
- Bean Validation mesajları JVM dilinde (Windows'ta tr: `'99' değerinden küçük yada eşit olmalı`); tüm servislerde aynı, girilen değer yok.
- Hibernate ORM 7.4.5.Final (Boot 4.1.1). MySQL kilit tuzağı: `jakarta.persistence.lock.timeout` pozitif değerde SQL'e yazılmaz,
  bağlantıya da uygulanmaz (`MySQLLockingSupport`); yalnızca -2 (SKIP LOCKED) ve 0 (NOWAIT) etkili. `PESSIMISTIC_WRITE` JPQL'i
  `for update of <alias>` üretir. SQL'i görmek için testte `-Dlogging.level.org.hibernate.SQL=DEBUG` (surefire'a geçer).
- Test tuzağı (tr-TR): `JdbcTemplate.queryForList/queryForMap` satır map'i (LinkedCaseInsensitiveMap) anahtarı JVM locale'iyle küçültür;
  information_schema'nın büyük harfli kolon adları (`ENGINE` → `engıne`) bulunamaz → SELECT'te küçük harfli takma ad ver.
- Ortak kütüphane: `common/` (`kitap-sepeti-common`, düz jar). Servis testleri `-am` ile common'ı da derler:
  `.\mvnw.cmd -pl catalog-service -am test`; yalnızca common: `.\mvnw.cmd -pl common test` (Docker gerekmez).
- Windows PowerShell 5.1: `Invoke-WebRequest -SkipHttpErrorCheck` yok; 4xx için try/catch kullan.
- Veritabanı: MySQL 8.4, kökteki `docker-compose.yml` ile (`container_name: kitapsepeti-mysql`, port 3306,
  volume `kitapsepeti_mysql_data`). Şifreler kökteki `.env`'de (git'e girmez), şablon `.env.example`.
- DB mimarisi: tek MySQL sunucusu, her servise ayrı şema + ayrı kullanıcı; servis kullanıcısı sadece kendi
  şemasına yetkili, root ile bağlanılmaz. `user-service` → şema `user_db`, kullanıcı `user_svc`
  (compose `MYSQL_DATABASE`/`MYSQL_USER`). `catalog-service` → `catalog_db`, `catalog_svc`
  (`infra/mysql/init/10-catalog-db.sh`, `.env` `CATALOG_DB_USER/PASSWORD`). Yeni servis şeması = yeni `NN-<servis>-db.sh`;
  mevcut volume'da elle `docker compose exec mysql sh /docker-entrypoint-initdb.d/<script>`. Tabloları her serviste Flyway oluşturur.
- catalog-service şeması (V1__create_catalog_tables.sql): publishers, authors, categories, books, book_authors,
  book_categories, stock_reservations, outbox. Aynı kurallar (BINARY(16) uygulama UUID'si, DATETIME(6) UTC, utf8mb4_0900_ai_ci,
  isimlendirilmiş pk_/uk_/fk_/ck_/ix_ kısıtları). Fiyat DECIMAL(12,2) + currency CHAR(3) 'TRY'.
- catalog-service: port 8082, çalıştırma `.\mvnw.cmd -pl catalog-service spring-boot:run`, testler `.\mvnw.cmd -pl catalog-service -am test`.
  Container: `docker compose build catalog-service` + `docker compose up -d` (imaj `kitapsepeti/catalog-service:local`, 595 MB;
  yerel `spring-boot:run` ile aynı anda çalışamaz). Health `/actuator/health/{liveness,readiness}`. Ayrıntı: `docs/docker.md`.
  Container'da profil yok → seed yüklenmez; seed'li yerel deneme için `spring-boot:run -Dspring-boot.run.profiles=local`.
- MinIO kapak depolama (Özellik 3): compose `minio`/`minio-init` imajı `pgsty/silo` (Docker Hub `minio/minio`+`minio/mc`
  2026-09’da kalktı; S3 API + `MINIO_*` uyumlu fork). Portlar 9000/9001; bucket `kitapsepeti-covers`, anonymous download.
  `.env` `S3_ACCESS_KEY`/`S3_SECRET_KEY` (yerel `minioadmin`/`minioadmin`, `${:?}`); catalog env: `S3_ENDPOINT=http://minio:9000`
  (PutObject), `S3_PUBLIC_BASE_URL=http://localhost:9000` (DB `cover_url` / tarayıcı — asla `minio:9000`). AWS SDK v2 S3 2.31.16;
  test profilinde `InMemoryCoverStorage`. Admin `POST /api/admin/books/{id}/cover` multipart; gateway `spring.codec.max-in-memory-size: 6MB`.
- PowerShell 5.1: `docker compose stop ...` gibi stderr'e ilerleme yazan komutlar `$ErrorActionPreference='Stop'` altında
  NativeCommandError ile script'i keser → `cmd /c "docker compose stop x 2>&1"`. `docker run ... sh -c '...'` içinde çift tırnak
  kaybolur; `find` parantezleri `\(` `\)` ile yazılır.
- Servisler arası anahtar (`.env`): `ORDER_INTERNAL_API_KEY` (order-service'in göndereceği ham anahtar, 32 bayt base64url) ve
  `CATALOG_INTERNAL_KEY_ORDER_SHA256` (aynı anahtarın SHA-256 hex'i; catalog yalnızca bunu okur). İkisi birlikte üretilir/değişir;
  ham anahtar hiçbir çıktıya yazılmaz (doğrulama yalnızca uzunluk + "hash eşleşiyor" boolean'ı ile). Sözleşme `docs/api/catalog-internal-stock.md`.
  cart-service: `CART_INTERNAL_KEY_ORDER_SHA256` (aynı anahtarın özeti; değer CATALOG_INTERNAL_KEY_ORDER_SHA256 ile aynıdır). Boşsa cart
  AÇILMAZ (catalog açılır). Testler anahtarı/özeti JVM'de üretir (`support/InternalTestKeys.register`, tam bağlam açan 4 test kökü).
- PowerShell tuzağı: değişken adları büyük/küçük harf DUYARSIZ (`$c` ile `$C` aynı değişken).
- Test tuzağı: JdbcTemplate'e `java.sql.Timestamp` verilirse Connector/J DATETIME'a JVM saat diliminde (UTC+3) yazar; Hibernate ise
  UTC (`hibernate.jdbc.time_zone`). Karşılaştırılacak zamanı JDBC ile yazarken `LocalDateTime.ofInstant(instant, ZoneOffset.UTC)` kullan.
- Kısa TTL ile yerel deneme: `.\mvnw.cmd -pl catalog-service spring-boot:run "-Dspring-boot.run.profiles=local"
  "-Dspring-boot.run.arguments=--app.stock.reservation-ttl=20s"` (görev ~30 sn'de bir çalışır).
- Mesajlaşma: RabbitMQ 4 (`rabbitmq:4-management`, container `kitapsepeti-rabbitmq`, AMQP `127.0.0.1:5672`,
  Management UI/API `127.0.0.1:15672`). Kullanıcı `.env`'deki `RABBITMQ_USER`/`RABBITMQ_PASSWORD`.
  user-service ve catalog-service olayları `kitapsepeti.events` (topic) exchange'ine outbox worker ile yayınlar;
  sözleşmeler `docs/events/`. Exchange'i iki servis de açılışta declare eder (tanım aynı olmalı).
- API dokümanı: springdoc-openapi 3.1.1 (Boot 4 hattı). Çalışırken `http://localhost:8081/swagger-ui.html` ve
  `/v3/api-docs` (OpenAPI 3.1.0); `SPRINGDOC_ENABLED=false` ile ikisi de kapanır. HTTP sözleşmeleri `docs/api/`
  (`user-service.openapi.json` = `/v3/api-docs` çıktısı; node ile `JSON.stringify(d, null, 2)` + BOM'suz yazıldı).
  catalog-service: `http://localhost:8082/swagger-ui.html`, sözleşme `docs/api/catalog-service.openapi.json` (OpenApiContractTest yazar).
  Admin denemesi: Authorize → bearerAuth'a user-service login `accessToken`'ı (role ADMIN); internal: internalApiKey'e `.env`
  `ORDER_INTERNAL_API_KEY`.
  cart-service: `http://localhost:8083/swagger-ui.html`, sözleşme `docs/api/cart-service.openapi.json` (yeniden üretim:
  `.\mvnw.cmd -pl cart-service test "-Dtest=OpenApiContractTest" "-Dopenapi.contract.update=true"`; common kurulu değilse `-am`
  + `-Dsurefire.failIfNoSpecifiedTests=false`). Sepet: bearerAuth'a herhangi bir kullanıcı token'ı; internal: `ORDER_INTERNAL_API_KEY`.
  payment-service: `http://localhost:8087/swagger-ui.html`, sözleşme `docs/api/payment-service.openapi.json` (yeniden üretim:
  `.\mvnw.cmd -pl payment-service test "-Dtest=OpenApiContractTest" "-Dopenapi.contract.update=true"`). internalApiKey'e
  `ORDER_INTERNAL_API_KEY`; webhook imzası Swagger'dan elle üretilmez (mock dispatcher kendisi gönderir).
  Docs/Swagger dört serviste de permitAll; Gateway fazında dışarıya kapatılacak.
- PowerShell tuzağı (tekrar yaşandı): `.env`'yi okurken `-match '^\s*([A-Za-z_]...'` tr-TR'de adında `I` geçen satırları ATLAR
  (`CATALOG_INTERNAL_KEY_ORDER_SHA256`, `RABBITMQ_USER`...) → değişken boş kalır (Order Adım 0a'dan beri catalog o zaman AÇILMAZ;
  öncesinde istemci kapalı açılıyordu). Her zaman `-cmatch` / Ordinal StartsWith.
  Ayrıca `Invoke-WebRequest().Content` UTF-8 yanıtı yanlış çözer; karşılaştırma için `WebClient.DownloadData` + UTF8.GetString.
- OS: Windows, shell: PowerShell

## Komutlar (kök dizinden)
- Tüm servisleri build: `.\mvnw.cmd clean package`
- Tek servis: `.\mvnw.cmd -pl user-service -am clean package`
- Testler: `.\mvnw.cmd -pl user-service test` — Docker açık olmalı (Testcontainers kendi MySQL'ini açar,
  `user_db`'ye dokunmaz).

## Hız kuralları (KULLANICI KURALI, Cart Adım 7'den itibaren kalıcı)
- Geliştirme sırasında yalnızca değiştirilen/eklenen test sınıfları koşulur:
  `.\mvnw.cmd -pl <modül> -am verify "-Dtest=<Sınıf1>,<Sınıf2>" "-Dsurefire.failIfNoSpecifiedTests=false"`.
- Modülün tam verify'ı (`.\mvnw.cmd -pl <modül> -am verify`) adım başına EN FAZLA 2 kez (biri adım sonunda).
- Kökten `.\mvnw.cmd clean verify` YALNIZCA: common değiştiyse, birden fazla modül değiştiyse ya da faz sonu adımında.
- Testcontainers reuse: kişisel ayar `~/.testcontainers.properties` içinde `testcontainers.reuse.enable=true` (repo'ya COMMIT
  EDİLMEZ; yoksa reuse kapalı, testler yine çalışır, her bağlam kendi konteynerini açar). Paylaşılan konteyner tanımlarında
  `.withReuse(true)`; MySQL'de modüle özgü `withDatabaseName("<modül>_test")` ŞART (reuse hash'i ayarlardan hesaplanır; aynı ayarlı
  iki modül aynı DB'yi ve Flyway geçmişini paylaşır). Boot 4.1.1 reuse'lu konteyneri bağlam kapanınca durdurmaz → tüm bağlamlar ve
  koşular aynı konteyneri kullanır, veri/kuyruk içeriği kalır (testler önceki veriye dayanıklı olmalı). Temizlik:
  `docker ps --filter "label=org.testcontainers.hash"` ile bulunur, `docker rm -f <id>`. Şu an cart-service (`cart_test`) ve
  order-service'te (`order_test`; Order Adım 8) açık, diğer modüller kendi adımlarında geçirilecek. Aynı ayarlı RabbitMQ tanımları da
  aynı hash'i üretir: ikinci modül ayırt edici bir etiket ekler (Order: `withLabel("com.kitapsepeti.test-module", "order")`).
  Aynı tabanı kullanan iki test sınıfı birebir aynı `@TestPropertySource`'u taşırsa bağlam paylaşılır (Order
  `PaymentResultListenerIT` + `PendingReconciliationJobIT`).
- Spring test bağlamı sayısı az tutulur: farklı `@MockitoSpyBean` / `@DynamicPropertySource` / `@TestPropertySource` kombinasyonu
  yeni bağlam açar; mümkünse ortak test tabanında toplanır. Cart ölçümü (Adım 7): 8 bağlam (CartServiceApplicationTests/ApiTestSupport,
  CatalogConnectionRefusedTest, CatalogDownCircuitBreakerTest, RabbitDownReadinessTest, CartCheckedOutListenerIT, CartLockingTest,
  CartSchemaConstraintsTest, JwksOutageTest). Reuse'suz 8 MySQL + 4 RabbitMQ başlatılıyordu; reuse ile 1 + 1.
  Order ölçümü (Adım 8): 7 bağlam (ApiTestSupport tabanı, PaymentResultListenerIT/PendingReconciliationJobIT, StockDispatcherIT,
  OrderLockingTest, OrderRepositoryTest, OrderSchemaConstraintsTest, StartupLogHygieneTest'in kendi uygulaması); reuse ile 6 MySQL +
  3 Rabbit başlatması mevcut konteyneri kullanır, yalnız StartupLogHygieneTest'in kasıtlı ayrı MySQL'i yeni. Modül süresi 2:38 → 1:35.
- E2E her adımda hafif (adımın tek ana senaryosu); geniş e2e Adım 8 ve 11'de.
- Rapor sonunda süre dökümü: derleme+test, docker build, e2e (dk).
