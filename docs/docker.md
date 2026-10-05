# Docker ile çalıştırma

Tüm komutlar repo kökünden. Değerler kökteki `.env`'den okunur (şablon: `.env.example`; `.env` git'e girmez).
Compose `env_file` kullanmaz; her servise yalnızca gereken değişkenler `${VAR}` ile verilir.

```powershell
docker compose build user-service catalog-service cart-service payment-service order-service
docker compose up -d
docker compose ps        # mysql, rabbitmq, user-service, catalog-service, cart-service, payment-service, order-service → healthy
```

Volume'ları silen `docker compose down -v` veritabanını ve kuyrukları da siler; durdurmak için `docker compose stop`.
Yerelde `spring-boot:run` ile açık bir servis varsa aynı portu kullandığı için önce o kapatılmalı.

Host portları yalnızca `127.0.0.1`'e bağlıdır (8081, 8082, 8083, 8087, 3306, 5672, 15672, adminer 8090): makinenin kendisinden
`localhost` ile erişilir, ağdaki başka cihazlardan erişilemez. Container'lar birbirine compose ağı üzerinden servis
adıyla bağlanır (`mysql:3306`, `rabbitmq:5672`, `user-service:8081`, `catalog-service:8082`); bu bağlama etkilenmez.

## catalog-service

| | |
|---|---|
| İmaj | `kitapsepeti/catalog-service:local` (`catalog-service/Dockerfile`, build context = repo kökü) |
| Container | `kitapsepeti-catalog-service`, port `8082` |
| Swagger UI | http://localhost:8082/swagger-ui.html (OpenAPI: `/v3/api-docs`) |
| Health | `/actuator/health/liveness`, `/actuator/health/readiness` (yalnızca `status`) |
| Profil | varsayılan; `local` verilmez, dev seed container'da uygulanmaz |

`.env`'de dolu olması gereken değişkenler:

| Değişken | Açıklama |
|---|---|
| `CATALOG_DB_USER`, `CATALOG_DB_PASSWORD` | `catalog_db` kullanıcısı (MySQL init script'i de aynı değerlerle oluşturur). |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Outbox olaylarının yayınlandığı broker kullanıcısı (user-service ile ortak). |
| `CATALOG_INTERNAL_KEY_ORDER_SHA256` | order-service anahtarının SHA-256 özeti (64 hex). Boşsa `/internal/**` her isteğe 401 döner. |

Compose'da sabit verilenler: `CATALOG_DB_HOST=mysql`, `RABBITMQ_HOST=rabbitmq`,
`USER_SERVICE_JWKS_URI=http://user-service:8081/.well-known/jwks.json`.

Bağımlılıklar:

- `mysql` ve `rabbitmq` healthy olmadan başlamaz. Readiness yalnızca DB'ye bakar: RabbitMQ kapalıyken servis healthy
  kalır, olaylar outbox'ta bekler ve broker dönünce gönderilir.
- user-service'e başlangıç bağımlılığı yoktur. JWKS ilk admin isteğinde çekilir ve 5 dk önbellekte tutulur.
  user-service kapalıyken public uçlar 200 döner; admin uçları önbellek süresi dolduktan sonra
  503 `AUTHENTICATION_UNAVAILABLE` döner.

Admin denemesi için Docker'daki user-service'ten (`http://localhost:8081/api/auth/login`) alınan ADMIN rolündeki
`accessToken`, Swagger'da `bearerAuth`'a girilir. Internal uçlar için `internalApiKey`'e `.env`'deki
`ORDER_INTERNAL_API_KEY` girilir.

## cart-service

| | |
|---|---|
| İmaj | `kitapsepeti/cart-service:local` (`cart-service/Dockerfile`, build context = repo kökü) |
| Container | `kitapsepeti-cart-service`, port `8083` |
| Swagger UI | http://localhost:8083/swagger-ui.html (OpenAPI: `/v3/api-docs`, sözleşme `docs/api/cart-service.openapi.json`) |
| Health | `/actuator/health/liveness`, `/actuator/health/readiness` (yalnızca `status`); diğer actuator uçları kapalı |

`.env`'de dolu olması gereken değişkenler (compose `${VAR:?}` ile zorunlu tutar; biri boşsa `docker compose` hata verir):

| Değişken | Açıklama |
|---|---|
| `CART_DB_USER`, `CART_DB_PASSWORD` | `cart_db` kullanıcısı (MySQL init script'i `20-cart-db.sh` de aynı değerlerle oluşturur). |
| `CART_INTERNAL_KEY_ORDER_SHA256` | `ORDER_INTERNAL_API_KEY`'in SHA-256 özeti (64 hex). Catalog'dan farklı olarak boşsa ya da 64 hex değilse cart-service açılmaz. |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `CartCheckedOut` olaylarının tüketildiği broker kullanıcısı (diğer servislerle ortak). |

Compose'da sabit verilenler: `CART_DB_HOST=mysql`, `USER_SERVICE_JWKS_URI=http://user-service:8081/.well-known/jwks.json`,
`CATALOG_BASE_URL=http://catalog-service:8082`, `RABBITMQ_HOST=rabbitmq`, `RABBITMQ_PORT=5672`. Token issuer'ı
(`kitapsepeti-user-service`) uygulama yapılandırmasında sabittir.

Bağımlılıklar:

- `mysql` ve `rabbitmq` healthy olmadan başlamaz (payment ile aynı; yalnızca başlangıç sırası). Readiness yalnızca DB'ye
  bakar; Catalog'u ya da user-service'i yoklayan health göstergesi yoktur (Spring Cloud'un `refreshScope`/`discoveryComposite`
  göstergeleri kapalı). RabbitMQ kapalıyken servis healthy kalır ve sepet uçları çalışır; kök `/actuator/health` `DOWN`
  görünür, sepet kapatma olayları broker'da bekler.
- Order'ın `CartCheckedOut` olayını (`cart.checked-out`) durable `cart.checkouts` kuyruğundan tüketir: aktif sepet
  `checked_out` olur, sonraki ekleme yeni sepet açar. Bozuk/eşleşmeyen mesaj doğrudan, geçici hata 3 denemeden sonra
  `kitapsepeti.dlx` üzerinden `cart.checkouts.dlq`'ya gider.
- catalog-service'e başlangıç bağımlılığı yoktur. Catalog kapalıyken cart healthy kalır; `GET /api/cart` ve sepeti
  değiştiren uçlar 200 döner (`catalogStatus: "UNAVAILABLE"`, satırlarda `available`/`currentUnitPrice` null, tutarlar
  sepete eklendiği andaki fiyattan); kitap ekleme 503 `CATALOG_UNAVAILABLE` döner (sepet değişmez). Catalog çağrısının
  bağlantı süresi 1 sn, okuma süresi 2 sn; yeniden deneme yok.
- user-service'e başlangıç bağımlılığı yoktur. JWKS ilk istekte çekilir ve 5 dk önbellekte tutulur; user-service
  kapalıyken önbellek süresi dolduktan sonra sepet uçları 503 `AUTHENTICATION_UNAVAILABLE` döner.

Yeniden üretme ve başlatma:

```powershell
docker compose build cart-service
docker compose up -d cart-service
docker compose logs -f cart-service
```

Sepet denemesi için Docker'daki user-service'ten alınan herhangi bir kullanıcının `accessToken`'ı Swagger'da `bearerAuth`'a
girilir. Internal snapshot için `internalApiKey`'e `.env`'deki `ORDER_INTERNAL_API_KEY` girilir.

## payment-service

| | |
|---|---|
| İmaj | `kitapsepeti/payment-service:local` (`payment-service/Dockerfile`, build context = repo kökü) |
| Container | `kitapsepeti-payment-service`, port `8087` |
| Swagger UI | http://localhost:8087/swagger-ui.html (OpenAPI: `/v3/api-docs`, sözleşme `docs/api/payment-service.openapi.json`) |
| Health | `/actuator/health/liveness`, `/actuator/health/readiness` (yalnızca `status`); diğer actuator uçları kapalı (403) |
| Sağlayıcı | yalnızca `mock` (kart verisi alınmaz ve saklanmaz) |

`.env`'de dolu olması gereken değişkenler (DB kullanıcısı, özet ve secret compose'da `${VAR:?}` ile zorunlu; biri boşsa
`docker compose` hata verir):

| Değişken | Açıklama |
|---|---|
| `PAYMENT_DB_USER`, `PAYMENT_DB_PASSWORD` | `payment_db` kullanıcısı (MySQL init script'i `30-payment-db.sh` de aynı değerlerle oluşturur). |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Outbox olaylarının (`payment.succeeded`, `payment.failed`) yayınlandığı broker kullanıcısı (diğer servislerle ortak). |
| `PAYMENT_INTERNAL_KEY_ORDER_SHA256` | `ORDER_INTERNAL_API_KEY`'in SHA-256 özeti (64 hex). Boşsa ya da 64 hex değilse payment-service açılmaz. |
| `PAYMENT_MOCK_WEBHOOK_SECRET` | Mock webhook imza anahtarı (HMAC-SHA256), en az 32 karakter. Boşsa ya da kısaysa payment-service açılmaz. |

Compose'da sabit verilenler: `PAYMENT_DB_HOST=mysql`, `PAYMENT_DB_PORT=3306`, `RABBITMQ_HOST=rabbitmq`,
`RABBITMQ_PORT=5672`. Mock webhook adresi verilmez: uygulama webhook'u container içinde kendi portuna
(`http://localhost:8087/webhooks/mock`) gönderir.

Bağımlılıklar:

- `mysql` ve `rabbitmq` healthy olmadan başlamaz (catalog ile aynı; yalnızca başlangıç sırası). Başka servise bağımlılık
  yoktur (JWT yok; order-service yalnızca istemci).
- Readiness yalnızca DB'ye bakar. RabbitMQ kapalıyken servis healthy kalır: ödeme oluşturma ve webhook çalışır, sonuç
  olayları outbox'ta bekler ve broker dönünce yayınlanır (kök `/actuator/health` bu sırada `DOWN` görünür; compose
  readiness'a bakar). Kesinti boyunca outbox her turda bir WARN yazar.

Mock akışı: `POST /internal/payments` ödemeyi `initiated` olarak oluşturur (201; aynı `orderId` ile tekrar 200). Kayıt
commit edildikten ~0,5 sn sonra mock sağlayıcı imzalı webhook'u uygulamanın kendi `/webhooks/mock` ucuna gönderir; ödeme
`succeeded` olur, kuruşu 99 olan tutarlar `failed` + `CARD_DECLINED`. Gönderim kaybolursa (servis yeniden başladı, kuyruk
doldu) kurtarma görevi 30 sn'de bir 10 sn'den eski `initiated` ödemelerin webhook'unu yeniden gönderir. Sonuç
`GET /internal/payments/{paymentId}` ile sorgulanır.

Yeniden üretme ve başlatma:

```powershell
docker compose build payment-service
docker compose up -d payment-service
docker compose logs -f payment-service
```

Internal uçlar için Swagger'da `internalApiKey`'e `.env`'deki `ORDER_INTERNAL_API_KEY` girilir. Webhook ucu imza ister;
Swagger'dan elle denemek yerine mock akışının kendisi kullanılır.

## order-service

| | |
|---|---|
| İmaj | `kitapsepeti/order-service:local` (`order-service/Dockerfile`, build context = repo kökü) |
| Container | `kitapsepeti-order-service`, port `8088` |
| Swagger UI | http://localhost:8088/swagger-ui.html (OpenAPI: `/v3/api-docs`, sözleşme `docs/api/order.openapi.json`) |
| Health | `/actuator/health/liveness`, `/actuator/health/readiness` (yalnızca `status`); diğer actuator uçları kapalı |

`.env`'de dolu olması gereken değişkenler (compose `${VAR:?}` ile zorunlu tutar; biri boşsa `docker compose` hata verir):

| Değişken | Açıklama |
|---|---|
| `ORDER_DB_USER`, `ORDER_DB_PASSWORD` | `order_db` kullanıcısı (MySQL init script'i `40-order-db.sh` de aynı değerlerle oluşturur). |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Olayların (`order.paid`, `order.failed`, `cart.checked-out`) yayınlandığı ve ödeme sonuçlarının tüketildiği broker kullanıcısı. |
| `ORDER_INTERNAL_API_KEY` | Cart, Catalog ve Payment `/internal/**` uçlarına giden ham anahtar. Boşsa uygulama açılmaz. |

Compose'da sabit verilenler: `ORDER_DB_HOST=mysql`, `ORDER_DB_PORT=3306`, `RABBITMQ_HOST=rabbitmq`, `RABBITMQ_PORT=5672`,
`USER_SERVICE_JWKS_URI=http://user-service:8081/.well-known/jwks.json`, `ORDER_CART_URL=http://cart-service:8083`,
`ORDER_CATALOG_URL=http://catalog-service:8082`, `ORDER_PAYMENT_URL=http://payment-service:8087`.

Bağımlılıklar:

- `mysql` ve `rabbitmq` healthy olmadan başlamaz. Readiness yalnızca DB'ye bakar.
- `cart-service`, `catalog-service` veya `payment-service` kapalıyken order-service healthy kalır; dış servis kesintileri
  circuit breaker ile yönetilir ve istemciye uygun ProblemDetail (502 `CART_UNAVAILABLE`, `CATALOG_UNAVAILABLE`, `PAYMENT_UNAVAILABLE`)
  olarak yansır.
- `user-service`'e başlangıç bağımlılığı yoktur. JWKS ilk istekte çekilir ve önbellekte tutulur.

Yeniden üretme ve başlatma:

```powershell
docker compose build order-service
docker compose up -d order-service
docker compose logs -f order-service
```
