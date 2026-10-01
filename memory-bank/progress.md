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
  DbConstraints birim testleri. 65 test yeşil. Henüz commit edilmedi.
  (Kapatılan bilinen sorun: "JWKS erişilemezken 500, Boot varsayılan JSON".)

## Yapılacaklar
- catalog-service: servis + API, springdoc, actuator, Dockerfile + compose servisi.
- Docker/Actuator adımının commit'i.
- Logout, e-posta/parola değiştirme, CORS.
- Diğer servisler (katalog, sepet, sipariş vb. — henüz kararlaştırılmadı) ve olay consumer'ları.
- CI pipeline (testler + image build).
- Gateway fazı backlog'u: kesintiye dayanıklı (outage-tolerant) JWKS önbelleği — anahtar önbellek süresi dolduktan sonra
  user-service/JWKS kapalıysa son bilinen anahtarla doğrulamaya devam (şu an önbellek süresi içinde 200, sonrasında 503).

## Bilinen sorunlar
- MySQL CHECK ihlali (3819, HY000) JdbcTemplate'te `UncategorizedSQLException` olur; JPA yolunda ise
  `DataIntegrityViolationException` (Hibernate çevirir). catalog-service'te CHECK'e dokunan yazımlar JPA üzerinden yapılmalı.
- Outbox'ta yayınlanmış satırlar temizlenmiyor; bilinmeyen `event_type` kuyruğun başını tıkar.
- DB kapalıyken `DataSourceHealthIndicator` her health çağrısında WARN + uzun stack trace yazıyor (log gürültüsü).
