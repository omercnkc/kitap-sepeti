# Project Brief

KitapSepeti: Spring Boot tabanlı, çok servisli (mikroservis) bir kitap satış uygulaması.

- Repo: https://github.com/omercnkc/kitap-sepeti (branch: `main`)
- Kök dizin Maven multi-module projesidir; her servis ayrı bir modül/klasördür.
- Mevcut servisler: `user-service` (8081), `catalog-service` (8082, tamamlandı), `cart-service` (8083, tamamlandı; OpenAPI sözleşmesi
  `docs/api/cart-service.openapi.json`, Docker + compose); ortak kütüphane modülü `common`.
- Geliştiriliyor: `payment-service` (Faz 7, 8087; Adım 1–7 bitti: iskelet + V1, alan modeli, V2 + güvenlik + internal ödeme ucu,
  sonuç servisi + outbox → RabbitMQ, imzalı mock webhook ucu, mock'un otomatik webhook gönderimi + kurtarma görevi — tam akış çalışıyor,
  OpenAPI sözleşmesi `docs/api/payment-service.openapi.json`; kalan: Docker + compose).
