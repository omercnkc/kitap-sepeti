# Progress

## Çalışanlar
- GitHub reposu bağlı; multi-module Maven yapısı (kök parent POM + `user-service`).
- Docker Compose: MySQL 8.4 (`user_db`) + RabbitMQ 4.
- user-service: Flyway V1 şeması, entity/repository, RS256 JWT + JWKS, kayıt/giriş/refresh (rotation),
  Resource Server + `/api/me` + adres CRUD, RFC 9457 hata altyapısı, outbox worker (publisher confirms,
  SKIP LOCKED), OpenAPI 3 dokümanı + Swagger UI + `docs/api/user-service.openapi.json`.
- Testler Testcontainers (MySQL + RabbitMQ) ile; 71 test yeşil.

## Yapılacaklar
- OpenAPI adımının commit'i.
- Logout, e-posta/parola değiştirme, CORS.
- Diğer servisler (katalog, sepet, sipariş vb. — henüz kararlaştırılmadı) ve olay consumer'ları.

## Bilinen sorunlar
- Outbox'ta yayınlanmış satırlar temizlenmiyor; bilinmeyen `event_type` kuyruğun başını tıkar.
- OpenAPI sözleşme dosyası elle üretiliyor; kodla uyumu otomatik kontrol edilmiyor.
