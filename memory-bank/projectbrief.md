# Project Brief

KitapSepeti: Spring Boot tabanlı, çok servisli (mikroservis) bir kitap satış uygulaması.

- Repo: https://github.com/omercnkc/kitap-sepeti (branch: `main`)
- Kök dizin Maven multi-module projesidir; her servis ayrı bir modül/klasördür.
- Mevcut servisler: `user-service` (8081), `catalog-service` (8082, tamamlandı), `cart-service` (8083, tamamlandı; OpenAPI sözleşmesi
  `docs/api/cart-service.openapi.json`, Docker + compose), `payment-service` (8087, tamamlandı — Faz 7 Adım 1–8: iskelet + V1, alan
  modeli, V2 + güvenlik + internal ödeme ucu, sonuç servisi + outbox → RabbitMQ, imzalı mock webhook ucu, mock'un otomatik webhook
  gönderimi + kurtarma görevi, OpenAPI sözleşmesi `docs/api/payment-service.openapi.json`, Docker + compose); ortak kütüphane modülü `common`.
- PROJE KARARI (Ekim 2026): sıra Payment → Order → Gateway → UI → (vakit kalırsa) Notifications. Notifications v1 yalnızca uygulama
  içi bildirim (OrderPaid/OrderFailed; e-posta, tercih, şablon yok). Order fazında `order-paid.md` ve `order-failed.md` olay
  sözleşmeleri yine yazılacak.
- Devam eden: `order-service` (8088; Adım 1 iskelet + `order_db` V1, Adım 2 domain, Adım 3a Cart/Catalog/Payment istemcileri + circuit
  breaker, Adım 3b Cart→Catalog circuit breaker, Adım 4 checkout + `GET /api/orders/{orderId}`, Adım 5 telafi ve yarıda kesilme
  yönetimi yapıldı; sıradaki Adım 6 StockSyncJob + ödeme olay tüketicisi).
