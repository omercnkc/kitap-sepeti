# Docker ile çalıştırma

Tüm komutlar repo kökünden. Değerler kökteki `.env`'den okunur (şablon: `.env.example`; `.env` git'e girmez).
Compose `env_file` kullanmaz; her servise yalnızca gereken değişkenler `${VAR}` ile verilir.

```powershell
docker compose build user-service catalog-service
docker compose up -d
docker compose ps        # mysql, rabbitmq, user-service, catalog-service → healthy
```

Volume'ları silen `docker compose down -v` veritabanını ve kuyrukları da siler; durdurmak için `docker compose stop`.
Yerelde `spring-boot:run` ile açık bir servis varsa aynı portu kullandığı için önce o kapatılmalı.

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
