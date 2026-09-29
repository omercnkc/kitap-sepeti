# Progress

## Çalışanlar
- GitHub reposu bağlı; multi-module Maven yapısı (kök parent POM + `user-service`).
- Docker Compose: MySQL 8.4 (`user_db`) + RabbitMQ 4 + user-service (non-root image, compose secrets, healthcheck).
- user-service: Flyway V1 şeması, entity/repository, RS256 JWT + JWKS, kayıt/giriş/refresh (rotation),
  Resource Server + `/api/me` + adres CRUD, RFC 9457 hata altyapısı, outbox worker (publisher confirms,
  SKIP LOCKED), OpenAPI 3 dokümanı + Swagger UI + `docs/api/user-service.openapi.json` (drift testi ile korunur),
  Actuator liveness/readiness.
- Testler Testcontainers (MySQL + RabbitMQ) ile; 83 test yeşil.

## Yapılacaklar
- Docker/Actuator adımının commit'i.
- Logout, e-posta/parola değiştirme, CORS.
- Diğer servisler (katalog, sepet, sipariş vb. — henüz kararlaştırılmadı) ve olay consumer'ları.
- CI pipeline (testler + image build).

## Bilinen sorunlar
- Outbox'ta yayınlanmış satırlar temizlenmiyor; bilinmeyen `event_type` kuyruğun başını tıkar.
- DB kapalıyken `DataSourceHealthIndicator` her health çağrısında WARN + uzun stack trace yazıyor (log gürültüsü).
