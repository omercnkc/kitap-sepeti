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
  amqp (Adım 4), mysql, common; springdoc/openfeign YOK (testle kilitli). Compose servis kaydı/Dockerfile yok. `spring-boot:run` DB,
  RabbitMQ (`RABBITMQ_HOST/PORT/USER/PASSWORD`, catalog ile aynı adlar) ve internal anahtar özetini `spring.config.import` ile
  `.env`'den kendisi okur. Testler RabbitMQ'yu `RabbitTestcontainersConfiguration` (rabbitmq:4-management) ile alır;
  outbox worker testte kapalı (`app.outbox.enabled: false`), relay testleri açar.
- payment internal anahtarı: `.env` `PAYMENT_INTERNAL_KEY_ORDER_SHA256` (Order'ın ham anahtarı `ORDER_INTERNAL_API_KEY`'in SHA-256 hex
  özeti; `.env.example`'da boş). Yok/boş/bozuk → payment-service açılmaz. Testler JVM'de üretilen anahtarı `InternalTestKeys.register`
  (DynamicPropertySource) ile verir.
- payment `app.payment.*` (`config/PaymentProperties`, @Validated): `provider` (varsayılan mock; v1'de yalnızca mock açılır),
  `mock.fail-cents` 0–99 (varsayılan 99). DİKKAT: `app.payment.provider=` (boş) hata vermez, Binder boş değeri null yapıp
  `@DefaultValue("mock")`'a düşer (testle sabitlendi). Bağlam testleri `ApplicationContextRunner().withUserConfiguration(PaymentConfig.class)`.
- Compose host portları YALNIZCA `127.0.0.1` (8081, 8082, 8083, 3306, 5672, 15672, adminer 8090): LAN'dan erişilmez, localhost'tan
  erişilir; container'lar arası servis adıyla erişim etkilenmez.
- Spring Cloud: release train `2025.1.3` (Oakwood; Boot 4.0.x/4.1.x, 4.1 desteği 2025.1.2'den itibaren), kök POM BOM import.
  Boot yükseltilirken spring.io/projects/spring-cloud uyumluluk tablosu yeniden kontrol edilir; milestone/RC ve milestone repo kullanılmaz.
  Şu an yalnızca cart-service kullanıyor (`spring-cloud-starter-openfeign` 5.0.3).
- cart-service: port 8083, şema `cart_db`, kullanıcı `.env` `CART_DB_USER/PASSWORD` (`infra/mysql/init/20-cart-db.sh`). Çalıştırma
  `.\mvnw.cmd -pl cart-service spring-boot:run` (common `~/.m2`'de değilse önce `.\mvnw.cmd -pl common -am install -DskipTests`),
  testler `.\mvnw.cmd -pl cart-service -am test`. Container: `docker compose build cart-service` + `docker compose up -d`
  (imaj `kitapsepeti/cart-service:local`, 618 MB disk / 189 MB içerik; uid 10001 `app`; yerel `spring-boot:run` ile aynı anda
  çalışamaz — ikisi de 8083). Compose env: `CART_DB_USER/PASSWORD`, `CART_INTERNAL_KEY_ORDER_SHA256` zorunlu (`${VAR:?}`), Catalog adresi
  `CATALOG_BASE_URL` (varsayılan `http://localhost:8082`; compose'da `http://catalog-service:8082`). Gerçek Catalog'a karşı test:
  `.\mvnw.cmd -pl cart-service test "-Dtest=CatalogLiveTest" "-Dcatalog.live=true"` (catalog seed'li olmalı). Hikari `connection-init-sql` ile
  `innodb_lock_wait_timeout = 5` (oturum; GLOBAL 50). Uçlar: `GET /api/cart`, `POST /api/cart/items` (USER token).
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
  Docs/Swagger üç serviste de permitAll; Gateway fazında dışarıya kapatılacak.
- PowerShell tuzağı (tekrar yaşandı): `.env`'yi okurken `-match '^\s*([A-Za-z_]...'` tr-TR'de adında `I` geçen satırları ATLAR
  (`CATALOG_INTERNAL_KEY_ORDER_SHA256`, `RABBITMQ_USER`...) → internal istemci kapalı açılır, anahtarla da 401. Her zaman `-cmatch`.
  Ayrıca `Invoke-WebRequest().Content` UTF-8 yanıtı yanlış çözer; karşılaştırma için `WebClient.DownloadData` + UTF8.GetString.
- OS: Windows, shell: PowerShell

## Komutlar (kök dizinden)
- Tüm servisleri build: `.\mvnw.cmd clean package`
- Tek servis: `.\mvnw.cmd -pl user-service -am clean package`
- Testler: `.\mvnw.cmd -pl user-service test` — Docker açık olmalı (Testcontainers kendi MySQL'ini açar,
  `user_db`'ye dokunmaz).
