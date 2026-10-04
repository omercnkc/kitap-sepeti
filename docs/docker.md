# Docker ile çalıştırma

Tüm komutlar repo kökünden. Değerler kökteki `.env`'den okunur (şablon: `.env.example`; `.env` git'e girmez).
Compose `env_file` kullanmaz; her servise yalnızca gereken değişkenler `${VAR}` ile verilir.

```powershell
docker compose build user-service catalog-service cart-service
docker compose up -d
docker compose ps        # mysql, rabbitmq, user-service, catalog-service, cart-service → healthy
```

Volume'ları silen `docker compose down -v` veritabanını ve kuyrukları da siler; durdurmak için `docker compose stop`.
Yerelde `spring-boot:run` ile açık bir servis varsa aynı portu kullandığı için önce o kapatılmalı.

Host portları yalnızca `127.0.0.1`'e bağlıdır (8081, 8082, 8083, 3306, 5672, 15672, adminer 8090): makinenin kendisinden
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

Compose'da sabit verilenler: `CART_DB_HOST=mysql`, `USER_SERVICE_JWKS_URI=http://user-service:8081/.well-known/jwks.json`,
`CATALOG_BASE_URL=http://catalog-service:8082`. Token issuer'ı (`kitapsepeti-user-service`) uygulama yapılandırmasında sabittir.

Bağımlılıklar:

- Yalnızca `mysql` healthy olmadan başlamaz. Readiness yalnızca DB'ye bakar; Catalog'u ya da user-service'i yoklayan
  health göstergesi yoktur (Spring Cloud'un `refreshScope`/`discoveryComposite` göstergeleri kapalı).
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
