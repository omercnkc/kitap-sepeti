# Progress

## Çalışanlar
- GitHub reposu bağlı; multi-module Maven yapısı (kök parent POM + `user-service`).
- Docker Compose: MySQL 8.4 (`user_db` + `catalog_db`) + RabbitMQ 4 + user-service (non-root image, compose secrets,
  healthcheck) + catalog-service (aynı imaj kalıbı, readiness yalnızca DB). Kullanım: `docs/docker.md`.
- **payment-service: TAMAMLANDI** (Faz 7 Adım 1–8; mock sağlayıcı, imzalı webhook, outbox → RabbitMQ, OpenAPI, Docker + compose
  8087). 365 test yeşil.
- **order-service: TAMAMLANDI** (Faz 8 Adım 0–11; checkout, durum makinesi, Outbox, stok telafi/sync, Feign + CB, uzlaştırma, liste, OpenAPI, Docker + compose 8088). 665 test yeşil.
- **api-gateway: TAMAMLANDI** (Faz 10 Adım 1–10 + test/koruma kanıtı: WebFlux gateway 8080, route/JWT/CORS/RequestId/internal blok/webhook, public path JWT bypass, Docker E2E). 41 test yeşil (8 sınıf).
- **TÜM BACKEND MİKROSERVİS MİMARİSİ TAMAMLANDI** (User, Catalog, Cart, Payment, Order ve API Gateway servisleri, MySQL, RabbitMQ, Docker Compose üzerinde canlı ve sağlıklı).
- **catalog-service: TAMAMLANDI** (Adım 1–11: şema, public okuma, admin CRUD, kitap yaşam döngüsü + stok, outbox olayları,
  internal stok rezervasyonu + süre dolumu, OpenAPI + drift testi, actuator, Docker + compose). 270 test yeşil.
- **Özellik 3 (MinIO kapak):** compose MinIO + minio-init; catalog `CoverStorage`/`CoverIngestService` (allowlist SSRF);
  admin multipart cover; gateway 6MB codec; admin form dosya yükleme. Testte InMemoryCoverStorage.
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
- Cart Adım 10 (commit `77ff615`, push edildi): `cart-service/Dockerfile` (catalog kopyası, non-root 10001) + compose kaydı (yalnızca mysql'e
  bağımlı, sırlar `${VAR:?}`), health yalnızca yerel bileşenler (Spring Cloud refreshScope/discoveryComposite kapalı), `docs/docker.md`
  cart bölümü. cart-service 312 test (2 skipped). Docker uçtan uca (sepet akışı, internal snapshot, Catalog kapalıyken UNAVAILABLE/503,
  restart kalıcılığı, JWKS) geçti. **Cart servisi tamamlandı.**

- Payment Adım 1 (commit `5c51261` + `b577ad9`, push edildi): `payment-service` modülü (8087; webmvc, data-jpa, flyway, validation, actuator, common;
  security/amqp/springdoc/openfeign yok), `infra/mysql/init/30-payment-db.sh` + compose mysql env (`PAYMENT_DB_*`, `${VAR:?}`),
  V1 (payments, provider_events, outbox — outbox catalog'la birebir). payment-service 60 test (49 şema/kısıt + 11 bağlam/health).
  Yerel `payment_db` + `payment_svc` (yetki yalnızca `payment_db.*`), V1 `spring-boot:run` ile uygulandı.
- Payment Adım 2 (commit `13bcd86` + `dbb3630`, push edildi): `Payment` (initiate → attachProviderReference; succeed/fail → TransitionResult;
  matches), `ProviderEvent` (@Immutable), katı küçük harf converter'lar (ortak taban), kilitsiz repository'ler, `PaymentProvider`
  soyutlaması + tek bean (`app.payment.provider`, yalnızca mock açılır), `MockPaymentProvider`, `MockOutcomeRule` (kuruş = fail-cents →
  CARD_DECLINED), `PaymentProperties` (fail-cents 0–99). payment-service 177 test.
- Order Adım 0a (commit `298fe89`, push edildi): common `RequestPathMasker` (desen + UUID güvenlik ağı; instance + tüm hata/internal logları;
  servis desenleri `SecurityConfig` bean'inde) ve tek internal özet politikası (`InternalApiKeys`: yok/boş/bozuk → açılmaz; catalog'un
  "boş = kapalı"sı kaldırıldı, compose `${VAR:?}`). cart/payment yerel maskeleme kodu silindi. Testler: common 39, user 85,
  catalog 284, cart 312, payment 365. Docker canlı kontrol geçti.
- Payment Adım 8 (commit `c735770` + `58914e0`, push edildi): `payment-service/Dockerfile` (cart kopyası, non-root 10001, 604 MB) + compose kaydı
  (127.0.0.1:8087; mysql + rabbitmq `service_healthy` — catalog gibi, yalnızca açılış sırası; sırlar `${VAR:?}`, env_file yok,
  webhook-url yok), health: readiness yalnızca db (bileşenler db, rabbit, diskSpace, livenessState, readinessState, ping, ssl),
  `docs/docker.md` payment bölümü, `WebhookEvent.providerPaymentId` `@Size(min = 1)` (sözleşme minLength 1). payment-service 365 test.
  Docker uçtan uca (succeeded/failed + olaylar, RabbitMQ kesintisinde healthy + outbox birikip boşalıyor, chunked 70 KB → 413,
  401/403/401, restart kalıcılığı) geçti. **Payment servisi tamamlandı.**
- Payment Adım 7 (commit `db2a9fe` + `e36bca7`, push edildi): OpenAPI — springdoc, `OpenApiConfig` (internalApiKey / mockWebhookSignature yol önekinden),
  `docs/api/payment-service.openapi.json` + drift testi, doküman/required-nullable testleri, kart verisi yokluğu testi (sınıf alanları +
  doküman adları), tam akış log hijyeni testi. Swagger UI `http://localhost:8087/swagger-ui.html`. payment-service 357 test.
- Payment Adım 6 (commit `fbf586c` + `b11d1ac`, push edildi): TAM AKIŞ ÇALIŞIYOR — referans yazılınca (commit sonrası) mock webhook 500ms sonra
  uygulamanın kendi `/webhooks/mock` ucuna imzalı gönderilir (`MockWebhookDispatcher`, sınırlı kendi zamanlayıcısı, tekrar yok),
  `MockRecoveryJob` (30 sn; 10 sn'den eski referanslı initiated mock ödemeleri yeniden gönderir, sabit eventId ile zararsız tekrar).
  Yerelde 149.90 → succeeded, 149.99 → failed CARD_DECLINED, RabbitMQ'da PaymentSucceeded/PaymentFailed. payment-service 339 test.
- Payment Adım 5 (commit `26bfbb5` + `c1ac219`, push edildi): imzalı mock webhook ucu `POST /webhooks/{provider}` (HMAC-SHA256 `X-Mock-Timestamp` +
  `X-Mock-Signature`, ±5 dk; ayrı güvenlik zinciri @Order 2), `WebhookService` (ödeme FOR UPDATE → tekrar → tutar → provider_events →
  PaymentResults, tek TX), `PAYMENT_MOCK_WEBHOOK_SECRET` zorunlu. payment-service 297 test.
- Payment Adım 4 (commit `1f5bce0` + `a98ad7a`, push edildi): `PaymentResults` (durum geçişi + outbox aynı TX), outbox → RabbitMQ yayıncısı
  (catalog kopyası, `kitapsepeti.events`, `payment.succeeded`/`payment.failed`), olay belgeleri. payment-service 247 test.
- Payment Adım 3 (commit `e5b4132` + `1dad3a0`, push edildi): V2 (sağlayıcı kimlikleri `utf8mb4_bin`), security (internal API key zinciri `order-service`
  + denyAll varsayılan zincir, anonim 403 challenge'sız), `POST /internal/payments` (idempotent, 201/200/409/503) +
  `GET /internal/payments/{id}`, yol maskeleme (log dahil). payment-service 220 test. Yerelde V2 uygulandı, uçtan uca geçti.

- Cart Adım 7 = Order planı Adım 7 (COMMIT EDİLMEDİ): CartCheckedOut tüketicisi (`cart.checkouts` + DLQ, Order 6a kalıbı; active →
  checked_out, sonraki ekleme yeni sepet; poison CART_NOT_FOUND/CART_OWNER_MISMATCH), common `EventsExchange`, compose cart-service
  rabbitmq bağımlılığı, CartConcurrencyTest 500 kök nedeni (ilk sepet INSERT deadlock 1213) düzeltildi (30/30). Testcontainers reuse
  cart'ta açık. Testler: kök `clean verify` common 69, user 86, catalog 285, cart 359 (2 skipped), payment 367, order 599. Docker
  cart-service healthy, kuyruk/binding doğru; hafif e2e (checkout → paid → sepet boş, DLQ boş) geçti. v1 sınırı: sipariş pending
  iken eklenen ürün sepetle birlikte kapanır (olayda satır listesi yok).
- Order Adım 8 (COMMIT EDİLMEDİ): `PendingReconciliationJob` (requested → CHECKOUT_INTERRUPTED; held → Payment'a aynı istek:
  succeeded → tüketiciyle aynı paid geçişi, failed → failed, initiated → ödeme bağla; sonuçsuzsa 10 dk'da ORDER_EXPIRED; circuit
  açıkken o tur Payment çağrısı yok), V3 `late_payment_at` + `ck_orders_late_payment_failed`, `Order.recordLatePayment` (failed
  siparişe geç başarı → kayıt + ERROR LATE_PAYMENT_SUCCESS; iade v1'de yok, admin iade listesi), Order consumer DLQ stack trace
  gürültüsü giderildi, Order Testcontainers reuse (`order_test`). Testler: common 69, order 643. Yerel E2E: V3 uygulandı, Adım 4
  kalıntısı ilk turda paid oldu (sepeti kapandı), rezervasyonu dolduğu için stok lost; yeni checkout → paid → committed → sepet kapandı.

- Order Adım 9 (COMMIT EDİLMEDİ): GET /api/orders?page=0&size=20 (Bearer) kullanıcının kendi siparişlerinin sayfalı özeti.
  Catalog ile birebir aynı sayfalama kuralları (varsayılan page 0, size 20; page >= 0, size 1..50; geçersiz değerlerde 400 VALIDATION_FAILED;
  sayfa zarfı items, page, size, 	otalElements, 	otalPages). Sıralama sabit created_at DESC, id DESC.
  Öğe OrderSummaryResponse (id, status, failureCode, currency, totalAmount, itemCount, createdAt, updatedAt). Yasaklı alanlar
  (address, items, stockState, paymentId, latePaymentAt) yok. N+1 yok (tek JPQL constructor projection + alt sorgu itemCount, sayım sorgusu
  userId ile sınırlı). ix_orders_user_created (user_id, created_at, id) indeksi Backward index scan ile kullanılır (filesort yok).
  1 ve 20 öğeli sayfalarda SQL sorgu sayısı sabit 2. Testler: common 69, order 657 (+14 test).

## Yapılacaklar
- UI (paralel, Ekim 2026): Order sürerken UI-0 → UI-6 ayrı worktree'de (`kitapSepeti-ui`, branch `ui`); durum
  `memory-bank/frontend.md`'de. UI için backend işleri: B1 catalog `q` araması, B2 Docker'da katalog örnek verisi, B3 ADMIN kullanıcı.
- PROJE KARARI (Ekim 2026): sıra Payment → Order → Gateway → UI → (vakit kalırsa) Notifications. Notifications v1 yalnızca uygulama
  içi bildirim (OrderPaid/OrderFailed; e-posta, tercih, şablon yok). Order fazında `order-paid.md` ve `order-failed.md` olay
  sözleşmeleri yine yazılacak.
- Payment artık işleri: iyzico sandbox; eşzamanlı ilk isteklerde birden fazla sağlayıcı çağrısı (gerçek sağlayıcıda idempotency
  anahtarı); outbox yayın hatası WARN'ındaki eventId (artık common `OutboxRelay`).
- Order Adım 0b YAPILDI (commit `df98e92` + `80dea5c`, push edildi): outbox `common.outbox`'ta (entity + repository dahil); servislerde
  yalnızca olay sınıfları, routing key eşlemesi, ince `OutboxPublisher` alt sınıfı ve `config/OutboxConfig`. Masker kapsama testi dört
  serviste. Testler: common 60, user 86, catalog 285, cart 313, payment 367.
- Order Adım 1 YAPILDI (commit `f190299`, push edildi): order-service modülü (8088) + `order_db` (init script 40, compose mysql env, `.env.example`),
  security iskeleti (JWT, denyAll), common outbox bağlantısı (boş routing key), V1 (orders, order_items, order_status_history, outbox).
  order 164 test; root verify: common 60, user 86, catalog 285, cart 313 (2 skipped), payment 367, order 164. Yerel `spring-boot:run`
  → V1 uygulandı, readiness UP.
- Order Adım 2 YAPILDI (commit `51feb91`, push edildi): entity'ler (Order, OrderItem, OrderStatusHistory, AddressSnapshot + açık JSON converter),
  durum makinesi (TransitionResult kalıbı), OrderRepository (FOR UPDATE, sahiplik, pending). KARAR: tamamı ücretsiz sepet →
  `422 ORDER_TOTAL_ZERO` sipariş yazılmadan önce (Adım 4/5). order 315 test; root verify yeşil.
- Order Adım 3a YAPILDI (commit `41d202c` + `cb759ea`, push edildi): Cart/Catalog/Payment Feign istemcileri (JDK HttpClient, log NONE, retry yok, istemci
  başına yalnızca `X-Internal-Api-Key` interceptor'ı), domain'e bakan gateway'ler + sealed sonuçlar (NotPerformed = istek
  ulaşmadı / Unknown = sonuç bilinmiyor), servis başına Resilience4j circuit breaker (20/10/%50/10 sn/3; yalnızca teknik hatalar),
  WireMock tabanlı eşleme/CB/başlık/log/sözleşme testleri. order 427 test; root verify yeşil.
- Order Adım 4 YAPILDI (COMMIT EDİLMEDİ): `POST /api/orders/checkout` (201 + Location, sipariş pending; adres gövdeden, user
  kuralları + country zorunlu) ve `GET /api/orders/{orderId}` (yalnızca sahibi; diğerleri 404 ORDER_NOT_FOUND). `CheckoutService`
  TX'siz, DB işleri `OrderTransactions` (READ_COMMITTED): pending kontrolü → Cart snapshot → Catalog lookup → `Order.place` → TX1
  insert → reserve → TX2 held → Payment initiate → TX3 attachPayment. Hata tablosu activeContext "Order Adım 4"te. Pom temizliği:
  Spring Cloud CB starter'ı + kapatma ayarları kaldırıldı (yalnızca resilience4j-circuitbreaker). Testler: order 427 → 489 (62 yeni
  checkout/okuma testi); root verify: common 65, user 86, catalog 285, cart 321 (2 skipped), payment 367, order 489. Yerel uçtan uca
  geçti (201 pending → 1 sn sonra hâlâ pending, rezervasyon held, Payment kaydı succeeded; ikinci checkout 409). Not: e2e kullanıcısının
  siparişi pending kaldı ve o kullanıcıyı engelliyor (Adım 6 tüketicisi / Adım 8 timeout gelene kadar).
- Order Adım 3b YAPILDI (commit `f651c07` + `8e3b037`): CB kurulumu `common.resilience`'a (CircuitBreakerProperties + CircuitBreakers; resilience4j
  optional, auto-config yok); Order aynı davranışla onu kullanıyor. Cart→Catalog tek `catalog` CB (20/10/%50/10 sn/3; teknik hata =
  hata, 4xx/stokta yok = başarı); açıkken istek gitmez, mevcut "Catalog yok" yanıtları aynen (ekleme 503, görünüm 200 UNAVAILABLE).
  Readiness etkilenmez. Testler: common 65, cart 321 (2 skipped); diğerleri aynı (user 86, catalog 285, payment 367, order 427).
  Docker: Catalog kapalıyken görünüm ~1030 ms → devre açıldıktan sonra ~24 ms; Catalog dönünce HALF_OPEN → CLOSED. Sıradaki: Adım 4.
- Cart ertelenenler: Catalog OpenAPI nullable. (CartCheckedOut tüketimi Cart Adım 7'de yapıldı.) (Yol maskeleme + özet politikası common'a
  taşındı → Order Adım 0a.)
- Order planı (PROJE KARARI, Ekim 2026): 0a common sertleştirme (yapıldı) → 0b outbox → common (yapıldı) → 1 modül/db (yapıldı) → 2 domain (yapıldı) → 3a Order istemcileri + CB (yapıldı)
  → 3b Cart→Catalog CB (yapıldı) → 4 checkout mutlu yol + GET {id} (yapıldı) → 5 hata yolları/telafi (YAPILDI: satır içi stok release, yarıda kesilme senaryoları a-e, 541 test) → 6a ödeme sonucu tüketicisi (YAPILDI) → 6b stok commit/release +
  StockSyncJob + V2 lost (YAPILDI) → 7 Cart CartCheckedOut tüketicisi (YAPILDI) → 8 timeout
  görevi (YAPILDI) → 9 liste → 10 OpenAPI/olay belgeleri → 11 Docker. Kararlar activeContext "Sonraki adımlar"da.
- Order Adım 6a YAPILDI (COMMIT EDİLMEDİ): projenin ilk RabbitMQ consumer'ı. Durable `order.payment-results`, direct
  `kitapsepeti.dlx`, durable DLQ ve iki payment binding'i; ortak, auto-config olmayan `DeadLetterQueueTopology`. Prefetch 10 /
  concurrency 1; poison doğrudan DLQ, geçici hata toplam 3 deneme (1s/2s, 4s tavan) sonra DLQ. PaymentSucceeded → paid +
  aynı TX'te OrderPaid/CartCheckedOut; PaymentFailed → failed + OrderFailed (Cart aktif); tekrar/çelişki ack. Checkout dahil failed
  olan her sipariş APPLIED'da OrderFailed üretir. Stok çağrısı/V2/job YOK (6b). Routing `order.paid`, `order.failed`,
  `cart.checked-out`; payloadlar eventId+v1, para metin. Kabul edilen 6b kararı: commit AlreadyReleased → paid+lost, tekrar yok,
  ERROR, DB admin listesi. Doğrulama: common 67, user 86, catalog 285, cart 321 (2 skipped), payment 367, order 560;
  ikinci kök `clean verify` yeşil (ilk denemede kapsam dışı Cart concurrency testi bir kez 500 üretti, tekil tekrar ve kök tekrar
  yeşil). Yerel E2E: yeni kullanıcıyla checkout 201 → paid; history 2; yayımlanmış OrderPaid+CartCheckedOut; başarısız mock
  senaryosu failed + yayımlanmış OrderFailed; geçici kuyruk üç tip/routing'i gördü ve silindi; DLQ boş; eski pending kalıntı duruyor.
- Order Adım 6b YAPILDI (COMMIT EDİLMEDİ): stok commit/release dispatcher (TransactionPhase.AFTER_COMMIT, ThreadPoolTaskExecutor
  2-4 core, 50 queue capacity, DiscardPolicy ile WARN; exception yok), StockCoordinator (TX dışında Catalog çağrısı, ardından
  `FOR UPDATE` ile durum güncelleme; AlreadyReleased/404 -> lost + ERROR STOCK_COMMIT_LOST; 401/Unknown -> held), StockSyncJob
  (fixedDelay 30s, min-age 10s, batch 50, tek instance varsayımı, (stock_state, updated_at) indeksi doğrulandı, devre kesicide erken
  sonlanma, tek INFO özeti), Flyway V2__stock_state_lost.sql (lost CHECK, ck_orders_lost_paid; V1'e dokunulmadı). Admin lost sorgusu
  şablonu eklendi. Testler: order 560 → 599 (+39 test); kök `mvnw test` / `verify` tam yeşil: common 67, user 86, catalog 285,
  cart 321 (2 skipped), payment 367, order 599. Yerel E2E: Docker servisleri + yerel order-service üzerinde V2 uygulandı;
  6a'dan kalan paid+held sipariş Catalog rezervasyon süresi dolduğu için lost + ERROR STOCK_COMMIT_LOST oldu; Adım 5 kalıntısı
  failed+held sipariş released oldu; yeni kullanıcıyla checkout -> paid -> committed döngüsü saniyeler içinde tamamlandı ve
  Catalog stoku 3'ten 2'ye düştü; ret yolu (.99 kuruş) katalog verisi değiştirilemeyeceği için atlandı; lost sorgusu 1 döndü.
- Order Adım 9 YAPILDI (COMMIT EDİLMEDİ): GET /api/orders sayfalı liste ucu, PageResponse<OrderSummaryResponse>, JPQL constructor projection + skaler alt sorgu ile tek SQL'de itemCount, backward index scan (filesort yok), 14 test, toplam 657 test yeşil.
- Order Adım 10 YAPILDI (COMMIT EDİLMEDİ): docs/api/order.openapi.json (OpenAPI 3.1.0), drift testi (OpenApiContractTest), required/nullable alanlar testi (OpenApiRequiredFieldsTest); docs/events/ altında order-paid.md, order-failed.md, cart-checked-out.md olay belgeleri; OrderEventsProducerContractTest, PaymentEventsConsumerContractTest ve CartCheckedOutContractTest (Cart'ın Order kodu derleme bağımlılığı kaldırıldı, doğrudan markdown'daki JSON örneğinden okur); OrderLogHygieneTest tek testte tüm akışları çalıştırarak UUID, tutar, adres test değerleri, Bearer ve internal API anahtarı hijyenini doğrular.
- Order Adım 11 YAPILDI (COMMIT EDİLMEDİ): order-service/Dockerfile (Temurin 21 layered jar), docker-compose.yml kitapsepeti-order-service (8088), mysql/rabbitmq bağımlılıkları, iç Feign URL'leri, DB-only readiness healthcheck, izolasyon (diğer servisler kapalıyken UP), docs/docker.md güncellendi. Faz 8 (Order Service) tamamlandı.
- Gateway fazı: docs/Swagger'ı (dört servis) dışarıya kapatmak.
- order-service (catalog rezervasyon istemcisi).
- Search için: yayınevi/yazar/kategori yeniden adlandırması yayındaki kitaplar için olay üretmiyor → yeniden indeksleme gerekecek.
- Backlog: admin PATCH'te bilinmeyen alanlar (stok, status) sessizce yok sayılıyor; ileride 400 düşünülebilir.
- Backlog (catalog OpenAPI): lookup/rezervasyon `status` "mixed" kodda üretilebiliyor, OpenAPI'de yok.
- Logout, e-posta/parola değiştirme, CORS.
- Diğer servisler (katalog, sepet, sipariş vb. — henüz kararlaştırılmadı) ve olay consumer'ları.
- CI pipeline (testler + image build).
- Gateway fazı backlog'u: kesintiye dayanıklı (outage-tolerant) JWKS önbelleği — anahtar önbellek süresi dolduktan sonra
  user-service/JWKS kapalıysa son bilinen anahtarla doğrulamaya devam (şu an önbellek süresi içinde 200, sonrasında 503).

## Bilinen sorunlar
- KARAR (Order Adım 6b): commit'te `AlreadyReleased` (rezervasyon süresi 15m doldu) → V2 `stock_state 'lost'` yalnız paid ile;
  tekrar commit yok, ERROR ve DB'den admin listesi. Adım 6a'da uygulanmadı. Pending zaman aşımı KARARI 10 dk (Adım 8; < 15 dk).
- Order (Adım 5 sonrası): Kayıt sonrası başarısız siparişlerde stok satır içi telafi (`releaseStock`) ile serbest bırakılır.
  Yarıda kesilme durumlarında (a-e) sipariş ve stok tutarlı bırakılır; `pending + requested/held` kalıntıları Adım 8 uzlaştırma
  görevi tarafından toplanacaktır. Adım 6a ile yeni ödeme olayları tüketilir; Adım 4'ten olayı kaybolmuş eski pending siparişleri
  ise Adım 8 kapatacaktır.
- Kök `/actuator/health` (catalog ve payment) RabbitMQ kapalıyken 503 DOWN ve her çağrıda stack trace'li WARN
  (RabbitHealthIndicator); readiness etkilenmez. Healthcheck'ler readiness kullanmalı.
- payment: aynı siparişe eşzamanlı ilk isteklerde sağlayıcı birden fazla çağrılabilir (yalnızca ilk referans yazılır, diğerleri WARN ile
  atılır). Mock'ta zararsız; gerçek sağlayıcıda sahipsiz ödeme oturumu kalabilir (idempotency anahtarı = paymentId ile çözülmeli).
- payment `app.payment.provider=` (boş) açılışı durdurmaz, varsayılan mock'a düşer (Spring Binder davranışı).
- cart flush sırası (Hibernate INSERT → UPDATE → DELETE; orphanRemoval da DELETE'i sona bırakır): aynı flush'ta satırı silip aynı
  kitabı eklemek `uk_cart_items_cart_book`, checkout + yeni aktif sepet `uk_carts_active_user` verir. Adım 6–7 akışları bu yolu
  kullanmaz (tekrar ekleme satırı UPDATE eder; silme/boşaltma yalnızca siler, yeniden ekleme ayrı istek). İleride satır silen/checkout
  yapan akış aynı TX'te yeniden ekleme/yeni sepet yaparsa arada `flush()` şart (CartTransactions javadoc'u).
- Yol maskeleme desen listesi elle tutulur (her servisin `SecurityConfig.requestPathMasker()` bean'i); yeni bir uç yolda id taşırsa
  desen eklenmeli. Eklenmezse güvenlik ağı yalnızca UUID biçimli segmentleri `:id` yapar (UUID olmayan değer görünür kalır).
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
  (Kapatılan bilinen sorun: payment sağlayıcı kimlikleri harf duyarsızdı → Payment Adım 3 V2 `utf8mb4_bin`.)
