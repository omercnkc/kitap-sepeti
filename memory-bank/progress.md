# Progress

## Çalışanlar
- GitHub reposu bağlı; multi-module Maven yapısı (kök parent POM + `user-service`).
- Docker Compose: MySQL 8.4 (`user_db` + `catalog_db`) + RabbitMQ 4 + user-service (non-root image, compose secrets,
  healthcheck) + catalog-service (aynı imaj kalıbı, readiness yalnızca DB). Kullanım: `docs/docker.md`.
- **catalog-service: TAMAMLANDI** (Adım 1–11: şema, public okuma, admin CRUD, kitap yaşam döngüsü + stok, outbox olayları,
  internal stok rezervasyonu + süre dolumu, OpenAPI + drift testi, actuator, Docker + compose). 270 test yeşil.
- user-service: Flyway V1 şeması, entity/repository, RS256 JWT + JWKS, kayıt/giriş/refresh (rotation),
  Resource Server + `/api/me` + adres CRUD, RFC 9457 hata altyapısı, outbox worker (publisher confirms,
  SKIP LOCKED), OpenAPI 3 dokümanı + Swagger UI + `docs/api/user-service.openapi.json` (drift testi ile korunur),
  Actuator liveness/readiness.
- Testler Testcontainers (MySQL + RabbitMQ) ile; 83 test yeşil.
- catalog-service iskeleti (port 8082): `catalog_db` + `catalog_svc` (init script `infra/mysql/init/10-catalog-db.sh`),
  Hikari/Flyway bağlanıyor.
- catalog-service V1 şeması (8 tablo) yerel `catalog_db`'ye uygulandı (commit `b32141e`).
- catalog-service entity + repository katmanı (validate geçiyor; commit `ed3a62c`, `afce68a`).
- catalog-service hata altyapısı (user-service kopyası + Catalog kodları/kısıt eşlemesi) ve OAuth2 Resource Server
  (user-service JWKS, RS256 + iss + exp, lazy JWKS); commit `c6cee96`.
- catalog-service JWKS kesintisi: token'lı korumalı istek 503 AUTHENTICATION_UNAVAILABLE ProblemDetail (eskiden 500);
  DbConstraints birim testleri. Commit `320d8fc`.
  (Kapatılan bilinen sorun: "JWKS erişilemezken 500, Boot varsayılan JSON".)
- catalog-service public okuma: GET `/api/books` (filtre: kategori+alt kategoriler, yayınevi, yazar, fiyat aralığı; 4 sıralama; sayfa),
  GET `/api/books/{id}`, GET `/api/categories` (ağaç). Liste 3 SQL. Local profilde idempotent örnek veri (`db/seed`).
  Commit `297c9bc`.
- catalog-service admin yazma uçları: yayınevi/yazar/kategori CRUD (`/api/admin/**`, ADMIN), addan Türkçe slug üretimi,
  kategori taşıma (`PUT /api/admin/categories/{id}/parent`, döngü → 409 CATEGORY_CYCLE), kullanımdaki kaydı silme → 409 RESOURCE_IN_USE.
  Commit `52194f4`.
- catalog-service kitap admin: `/api/admin/books` liste/detay/oluştur/PATCH (istemci versiyonu zorunlu)/publish/archive/DELETE(=arşiv),
  koşullu stok düzeltme (`stock-adjustments`), ISBN normalizasyon + checksum, `BookUpserted`/`BookRemoved` outbox olayları
  (`docs/events/book-upserted.md`, `book-removed.md`). Stok kolonları entity'den yazılamaz. 177 test yeşil. Commit `366c465`.
- catalog-service outbox relay: user-service worker'ının kopyası; `book.upserted` / `book.removed` routing key'leriyle ortak
  `kitapsepeti.events` exchange'ine yayın (tanım user-service ile birebir). Gerçek broker kesintisi testi dahil 194 test yeşil.
  Commit `c03c0de`.
  (Kapatılan bilinen sorun: "catalog-service outbox'ı henüz yayınlanmıyor".)
- catalog-service internal stok rezervasyonu: `/internal/stock/reservations` reserve (ya hep ya hiç, idempotent `orderId`) / commit /
  release / GET; servisler arası `X-Internal-Api-Key` (SHA-256 özetle doğrulama, ayrı security zinciri); inStock değişince BookUpserted.
  Eşzamanlılık testleri dahil 225 test yeşil. Commit `958a708` (+ memory-bank `8a56ed8`), push edildi.
- catalog-service rezervasyon süre dolumu (Adım 9): `ReservationExpiryJob` (~30 sn, tur başına 100 sipariş, sipariş başına ayrı
  transaction + `FOR UPDATE SKIP LOCKED`, iptal ucuyla ortak `releaseHeld`). Seed'e rezervasyon satırları eklendi; değişmez
  `reserved_quantity = SUM(held)` testlerde kontrol ediliyor. 248 test yeşil. Commit `e18960a`, push edildi.
  (Kapatılan bilinen sorunlar: "süresi dolan held rezervasyonlar serbest bırakılmıyor", "seed'de satırsız rezerv".)
- catalog-service OpenAPI (Adım 10): Swagger UI `http://localhost:8082/swagger-ui.html`, `bearerAuth` (admin) / `internalApiKey`
  (internal) / public kilitsiz, tüm hatalar `application/problem+json` (`Problem`, rezervasyon 409'u `StockUnavailableProblem` + `bookIds`).
  Sözleşme `docs/api/catalog-service.openapi.json` (19 path / 31 operasyon), drift testi ile korunur. 260 test yeşil. Commit `5c30ca5` (+ memory-bank `725144f`), push edildi.
- catalog-service Adım 10b: rezervasyon `unitPrice` JSON sayı (`priceAmount` ile aynı biçim, 2 ondalık); tüm response şemalarında
  her zaman dolu alanlar `required` (opsiyonel: kitap isbn/description/pageCount/coverUrl/publishedAt, kategori parentId).
  `OpenApiRequiredFieldsTest` gerçek yanıtları şemaya karşı doğrular. 262 test yeşil. Commit `3c40d4d`, push edildi.
- catalog-service Adım 11: Actuator (user-service ile aynı: yalnızca health, readiness = readinessState + db), `catalog-service/Dockerfile`
  (user-service kalıbı), compose `catalog-service` (8082, healthcheck, user-service'e bağımlılık yok), `docs/docker.md`.
  `BookUpserted.priceAmount` metin kaldı (MySQL JSON kolonu sayıyı DOUBLE'a çevirip sondaki sıfırları atıyor; doküman güncellendi).
  270 test yeşil. Commit `b128320` + `9443b82`, push edildi.
- Cart Adım 0 (henüz commit edilmedi): `common` modülü (`kitap-sepeti-common`; ErrorCode arayüzü + CommonErrorCode, ProblemDetail
  handler tabanı, DbConstraints genel kısmı, 401/403/503 handler'ları, rol dönüştürücü, BearerTokenResolver yardımcıları, JWKS
  JwtDecoder yardımcısı, internal API anahtarı filtresi). İki servis ona geçti; sözleşmeler byte-aynı. Dockerfile'lar `COPY --parents`.
  Compose host portları yalnızca 127.0.0.1. Testler: common 25, user-service 83, catalog-service 262. Commit + push edildi.
- cart-service Adım 1 (henüz commit edilmedi): modül iskeleti (8083), Spring Cloud 2025.1.3 BOM + OpenFeign (client yok),
  geçici güvenlik (yalnızca health açık), `cart_db` init script'i, V1 (carts + cart_items, kısıt testleri; `carts.status`
  `utf8mb4_bin`). cart-service 42 test. Yerel `cart_db` oluşturuldu (yetki yalnızca `cart_db.*`), V1 `spring-boot:run` ile uygulandı,
  health UP. Commit `2b31367` + `45b432c`, push edildi.
- cart-service Adım 2: `Cart` (aggregate root) + `CartItem` + `CartStatus`/converter, `CartRepository`
  (EntityGraph ile tek sorgu okuma, `FOR UPDATE` kilit), kilit beklemesi 5 sn (Hikari `connection-init-sql`). cart-service 90 test.
  Commit `333d19a` + `376ab3a`, push edildi.
- cart-service Adım 3: kalıcı Resource Server güvenliği (JWKS, lazy; JWKS kesintisi 503), `@CurrentUserId`
  (sub → UUID, geçersiz sub 401 invalid_token), `CartErrorCode` + API_CODES, limit/kitap/catalog exception'ları, GlobalExceptionHandler +
  DbConstraintCodes, ClockConfig. Test anahtarı çalışma anında üretiliyor. cart-service 150 test; uçtan uca a–d geçti.
  Commit `2119d3f` + `76e8a46`, push edildi.
- Cart Adım 4 (catalog-service): public `GET /api/books/lookup?ids=...` (en fazla 50; yalnızca yayındakiler,
  istek sırası, tekrarsız; sabit 2 SQL). Sözleşmeye yeni path + `BookLookupResponse`. catalog-service 276 test. cart bunu
  `GET /api/cart`'ta kullanacak. Commit `c0e1c19` + `0455964`, push edildi.
- Cart Adım 5: `CatalogClient` (Feign) + `CatalogGateway` (hata eşleme: 404/stokta değil → 409, kesinti → 503,
  diğer 4xx → 500), timeout 1/2 sn, retry yok, token taşınmıyor; JDK HttpServer stub'ı, tüketici sözleşme testi, gerçek Catalog'a
  karşı opsiyonel test. cart-service 197 test (2 skipped). Commit `66b3d01` + `99a04fa`, push edildi.
- Cart Adım 6 (commit `3a378d6` + `f95ad17`, push edildi): `GET /api/cart` (kilitsiz tek SQL + TX dışında tek Catalog lookup; Catalog yoksa 200
  UNAVAILABLE + snapshot fiyatları) ve `POST /api/cart/items` (Catalog TX'ten önce; sepet aç/adet artır/satır ekle READ COMMITTED
  TX'te; ilk sepet yarışında bir kez yeniden deneme; limitler 409 + `limit`). Tüm zamanlar Clock'tan. Eşzamanlılık testleri dahil
  cart-service 237 test (2 skipped); uçtan uca a–h geçti.
- Cart Adım 7 (commit `c692567` + `a3b7abe`, push edildi): `PATCH /api/cart/items/{bookId}` (adet; Catalog doğrulaması/snapshot yenileme yok; yoksa 404,
  limit 409), `DELETE /api/cart/items/{bookId}` (idempotent 200) ve `DELETE /api/cart/items` (satırlar silinir, sepet aktif kalır).
  Değişiklik yoksa damgalama yok. Yoldaki kitap id'si hata yanıtında/logda `:bookId` olarak maskelenir. cart-service 264 test
  (2 skipped); uçtan uca a–f geçti.

- Cart Adım 8 (commit `2135ac6` + `dd01c0c`, push edildi): `POST /internal/cart/snapshot` (X-Internal-Api-Key, catalog ile aynı
  @Order(1) zinciri; salt okunur tek SQL, aktif sepet yoksa cartId null). `CART_INTERNAL_KEY_ORDER_SHA256` yoksa/bozuksa cart açılmaz.
  cart-service 287 test (2 skipped). Gerçek anahtarla uçtan uca 200, Adım 9 ön adımında doğrulandı.
- Cart Adım 9 (commit `6fcd291` + `bb2e0cf`, push edildi): OpenAPI — springdoc, `OpenApiConfig` (yol önekine göre bearerAuth/internalApiKey, standart
  hatalar, `CartLimitProblem`), sözleşme `docs/api/cart-service.openapi.json` (4 path, 6 operasyon, internal dahil), drift +
  docs + required/nullable testleri. cart-service 301 test (2 skipped).
- Cart Adım 10 (henüz commit edilmedi): `cart-service/Dockerfile` (catalog kopyası, non-root 10001) + compose kaydı (yalnızca mysql'e
  bağımlı, sırlar `${VAR:?}`), health yalnızca yerel bileşenler (Spring Cloud refreshScope/discoveryComposite kapalı), `docs/docker.md`
  cart bölümü. cart-service 312 test (2 skipped). Docker uçtan uca (sepet akışı, internal snapshot, Catalog kapalıyken UNAVAILABLE/503,
  restart kalıcılığı, JWKS) geçti. **Cart servisi tamamlandı.**

- Payment Adım 1 (henüz commit edilmedi): `payment-service` modülü (8087; webmvc, data-jpa, flyway, validation, actuator, common;
  security/amqp/springdoc/openfeign yok), `infra/mysql/init/30-payment-db.sh` + compose mysql env (`PAYMENT_DB_*`, `${VAR:?}`),
  V1 (payments, provider_events, outbox — outbox catalog'la birebir). payment-service 60 test (49 şema/kısıt + 11 bağlam/health).
  Yerel `payment_db` + `payment_svc` (yetki yalnızca `payment_db.*`), V1 `spring-boot:run` ile uygulandı.

## Yapılacaklar
- Payment (Faz 7) sonraki adımlar: entity/repository, internal ödeme oluşturma ucu (API key, Order), mock sağlayıcı + imzalı webhook
  (HMAC), outbox + RabbitMQ ile sonucun Order'a gitmesi, OpenAPI, Docker + compose (Adım 8).
- Cart ertelenenler: CartCheckedOut tüketimi (Order fazı); yol maskeleme + boş özet politikasını common'a taşıma; Catalog OpenAPI nullable.
- Gateway fazı: docs/Swagger'ı (üç servis) dışarıya kapatmak.
- Outbox kodunu common'a taşıma (ayrı adım; şu an servis başına kopya).
- order-service (catalog rezervasyon istemcisi).
- Search için: yayınevi/yazar/kategori yeniden adlandırması yayındaki kitaplar için olay üretmiyor → yeniden indeksleme gerekecek.
- Backlog: admin PATCH'te bilinmeyen alanlar (stok, status) sessizce yok sayılıyor; ileride 400 düşünülebilir.
- Backlog: Spring AMQP `CachingConnectionFactory` INFO satırı ("Created new connection … amqp://<kullanıcı>@rabbitmq") RabbitMQ
  kullanıcı adını loga yazıyor (parola yok; user-service ve catalog-service). İstenirse o logger WARN'a çekilebilir.
- Logout, e-posta/parola değiştirme, CORS.
- Diğer servisler (katalog, sepet, sipariş vb. — henüz kararlaştırılmadı) ve olay consumer'ları.
- CI pipeline (testler + image build).
- Gateway fazı backlog'u: kesintiye dayanıklı (outage-tolerant) JWKS önbelleği — anahtar önbellek süresi dolduktan sonra
  user-service/JWKS kapalıysa son bilinen anahtarla doğrulamaya devam (şu an önbellek süresi içinde 200, sonrasında 503).

## Bilinen sorunlar
- cart flush sırası (Hibernate INSERT → UPDATE → DELETE; orphanRemoval da DELETE'i sona bırakır): aynı flush'ta satırı silip aynı
  kitabı eklemek `uk_cart_items_cart_book`, checkout + yeni aktif sepet `uk_carts_active_user` verir. Adım 6–7 akışları bu yolu
  kullanmaz (tekrar ekleme satırı UPDATE eder; silme/boşaltma yalnızca siler, yeniden ekleme ayrı istek). İleride satır silen/checkout
  yapan akış aynı TX'te yeniden ekleme/yeni sepet yaparsa arada `flush()` şart (CartTransactions javadoc'u).
- Cart hata yanıtı/logunda yalnızca `/api/cart/items/<x>` maskelenir (`MaskedRequestPaths`); yeni bir uç yolda kullanıcıya/kitaba özgü
  id taşırsa oraya da eklenmeli.
- catalog'un `FOR UPDATE` sorgularında kilit süresi yok → MySQL varsayılanı 50 sn bekler (cart'ta 5 sn'ye çekildi; catalog'a dokunulmadı).
- catalog/user durum kolonları `utf8mb4_0900_ai_ci`: catalog `books.status` (`ck_books_status`), `stock_reservations.status`
  (`ck_stock_reservations_status`), user `users.status` (`ck_users_status`; `ck_users_role` de aynı). CHECK büyük/küçük harf
  duyarsız ('PUBLISHED' geçer). Uygulama küçük harf yazdığı için risk düşük, V2 gerekmez; ham SQL yazımında dikkat.
  (cart-service'te düzeltildi: `carts.status COLLATE utf8mb4_bin`; yeni servisler için kural systemPatterns'te.)
- MySQL CHECK ihlali (3819, HY000) JdbcTemplate'te `UncategorizedSQLException` olur; JPA yolunda ise
  `DataIntegrityViolationException` (Hibernate çevirir). catalog-service'te CHECK'e dokunan yazımlar JPA üzerinden yapılmalı.
- Outbox'ta yayınlanmış satırlar temizlenmiyor; bilinmeyen `event_type` kuyruğun başını tıkar (user-service ve catalog-service).
- Stok taşması: `initialStock` ≤ 1.000.000, düzeltme |delta| ≤ 100.000; INT sınırına ancak çok sayıda düzeltmeyle yaklaşılır,
  o zaman `+delta` MySQL out-of-range → 500 (pratikte olası değil).
- DB kapalıyken `DataSourceHealthIndicator` her health çağrısında WARN + uzun stack trace yazıyor (log gürültüsü).
- Seed yeniden çalışınca seed kitaplarının stok/rezervi seed değerine döner; o kitaplarda seed dışı `held` rezervasyon varsa
  değişmez bozulur (yalnızca local dev verisi).
- Süre dolumu görevi tutarsız bir siparişi (kitap rezervi < adet) her turda yeniden dener ve her seferinde WARN yazar; elle düzeltilene
  kadar o sipariş `held` kalır.
- Tıkanan süre dolumu siparişleri kuyruğun başını tıkayabilir (batch dolarsa), outbox'taki tanınmayan event_type sorunuyla birlikte çözülecek.
- catalog ve cart `/v3/api-docs` ve Swagger UI her profilde açık (compose dahil; `SPRINGDOC_ENABLED=false` ile kapanır); internal uç
  şekilleri de görünür (sır yok). Gateway fazında kapatılacak.
- catalog JWKS önbelleği 5 dk: user-service kapandıktan sonra admin uçları önbellek süresi boyunca 200, sonra 503 (Gateway fazı backlog'u).
- `BookUpserted.priceAmount` metin, HTTP'deki `priceAmount` sayı (outbox payload kolonu MySQL JSON; sayı DOUBLE'a dönüşür).
  Sayıya geçmek için payload kolonunu metin tipine çeviren yeni migration + `eventVersion` kararı gerekir.
  (Kapatılan bilinen sorun: "yeni `<module>` için her Dockerfile'a pom COPY satırı" → `COPY --parents */pom.xml`.)
