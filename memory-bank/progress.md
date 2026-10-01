# Progress

## Çalışanlar
- GitHub reposu bağlı; multi-module Maven yapısı (kök parent POM + `user-service`).
- Docker Compose: MySQL 8.4 (`user_db`) + RabbitMQ 4 + user-service (non-root image, compose secrets, healthcheck).
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

## Yapılacaklar
- order-service (catalog rezervasyon istemcisi).
- catalog-service: springdoc, actuator, Dockerfile + compose servisi.
- Search için: yayınevi/yazar/kategori yeniden adlandırması yayındaki kitaplar için olay üretmiyor → yeniden indeksleme gerekecek.
- Docker/Actuator adımının commit'i.
- Logout, e-posta/parola değiştirme, CORS.
- Diğer servisler (katalog, sepet, sipariş vb. — henüz kararlaştırılmadı) ve olay consumer'ları.
- CI pipeline (testler + image build).
- Gateway fazı backlog'u: kesintiye dayanıklı (outage-tolerant) JWKS önbelleği — anahtar önbellek süresi dolduktan sonra
  user-service/JWKS kapalıysa son bilinen anahtarla doğrulamaya devam (şu an önbellek süresi içinde 200, sonrasında 503).

## Bilinen sorunlar
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
