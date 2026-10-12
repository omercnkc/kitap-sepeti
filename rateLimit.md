# Rate limit v1 — yol haritası

Hedef: **~2 gün**, yalnızca `api-gateway` + Redis + compose. Downstream servislere dokunulmaz.

Şu an: HTTP rate limit yok. Tek dış giriş `api-gateway` (:8080).

---

## Adım 0 — Kararlar (30 dk, kod yok)

Durum: ✅ tamamlandı — 2026-10-12

### Adım 0 — Kabul edilen kararlar (kilitli)

**A) Fail davranışı (v1):** FAIL-OPEN  
Redis erişilemezse istek rate limit uygulanmadan devam eder. Prod fail-closed sonraya bırakılır (aşağıda “v1 dışı”).

**B) KeyResolver:** JWT `sub` (+ IP fallback)  
- JWT zorunlu uçlar (cart, checkout, `/api/me`, orders vb.): kota anahtarı = JWT **`sub`**  
- `sub` yoksa / parse edilemezse: **IP** fallback  
- JWT’siz uçlar (auth, public catalog GET, webhook): **IP** (`X-Forwarded-For` ilk hop, yoksa `remoteAddress`)  
- `/internal/**`: rate limit yok (gateway zaten 404)

**C) Tier tablosu (v1 başlangıç; env ile sonra ayarlanır):**

| Tier | Path | Limit | Key |
|------|------|-------|-----|
| auth | `/api/auth/**` | 10 req / 60s | IP |
| catalog-read | `GET /api/books/**`, `/api/categories/**` | 120 / 60s | IP |
| cart | `/api/cart/**` | 60 / 60s | user (`sub`) |
| checkout | `POST /api/orders/checkout` | 5 / 60s | user (`sub`) |
| default | diğer `/api/**` | 100 / 60s | user→IP |
| webhook | `/webhooks/**` | 60 / 60s | IP |

Adım 0–7 tamam; sıradaki: **Adım 8 — Doğrulama & dokümantasyon**.

---

## Adım 1 — Redis infra (2–3 saat)

Durum: ✅ tamamlandı — 2026-10-12

**Ne eklendi:** `redis:7-alpine` (`kitapsepeti-redis`, `127.0.0.1:6379`, healthcheck `redis-cli ping`, volume yok); `api-gateway` → `depends_on: redis` (healthy) + `REDIS_HOST`/`REDIS_PORT`; `.env.example` + `docs/docker.md` Redis notları.

---

## Adım 2 — Gateway bağımlılıkları (1 saat)

Durum: ✅ tamamlandı — 2026-10-12

**Ne eklendi:** `spring-boot-starter-data-redis-reactive`; `spring.data.redis.host/port` (`REDIS_HOST`/`REDIS_PORT`); `management.health.redis.enabled=false` (FAIL-OPEN — Redis DOWN iken gateway health/startup bozulmaz). Route filter / KeyResolver yok.

**Dokunulan:** `api-gateway/pom.xml`, `application.yml`

---

## Adım 3 — KeyResolver (2–3 saat)

Durum: ✅ tamamlandı — 2026-10-12

**Ne eklendi:** bean `ipKeyResolver` (XFF ilk hop → remoteAddress → `unknown`); bean `userOrIpKeyResolver` (@Primary; `user:<X-User-Id>` veya `ip:<aynı IP mantığı>`). JWT yeniden parse yok; `X-User-Id` JwtAuthGlobalFilter sonrası. Route `RequestRateLimiter` yok.

**Dokunulan:** `api-gateway/.../ratelimit/`, unit test

---

## Adım 4 — Route bazlı limitler (2–3 saat)

Durum: ✅ tamamlandı — 2026-10-12

**Ne eklendi:** route id’ler `auth`, `user-me`, `catalog-public` (GET), `catalog-write`, `catalog-admin`, `cart`, `orders-checkout` (POST), `orders`, `search`, `notification`, `payment-webhook` + `RequestRateLimiter`. Tier token-bucket (`app.rate-limit.*` / env): auth 1/10, catalog-read 2/120, cart 1/60, checkout 1/5, default 2/100, webhook 1/60. Search/notification: default tier (skip yok). 429 body hâlâ Gateway default (Adım 5).

**FAIL-OPEN:** `RedisRateLimiter` Redis hata/timeout’ta allow (`onErrorResume` → allowed); kısa `connect-timeout`/`timeout`. Bilinen kısıt: Redis yavaşken istek latency artabilir (timeout kadar bekler), yine de 429 değil allow.

**Dokunulan:** `api-gateway/.../application.yml`, `src/test/resources/application.properties` (yalnızca env override; test’te `application.yml` koyma — main yml’i ezer), route config testi

---

## Adım 5 — 429 yanıtı (2–3 saat)

Durum: ✅ tamamlandı — 2026-10-12

**Ne eklendi:** `throw-on-limit: true` + `RateLimitExceededExceptionHandler` (`WebExceptionHandler` @Order -2). Body: `type=about:blank`, `title=İstek sınırı aşıldı`, `status=429`, `detail` (TR), `instance=path`, `code=RATE_LIMIT_EXCEEDED`; `Content-Type: application/problem+json`. **Retry-After:** sabit `app.rate-limit.retry-after-seconds` (varsayılan 60); X-RateLimit-* kalan token’dan türetilmez. X-Request-Id bozulmaz.

**Dokunulan:** `RateLimitExceededExceptionHandler`, `application.yml`, unit test

---

## Adım 6 — Testler (3–4 saat)

Durum: ✅ tamamlandı — 2026-10-12

**Ne eklendi:** `RateLimitIT` — Testcontainers `RedisContainer` (`redis:7-alpine`, `@ServiceConnection`); IT-only düşük `AUTH_RATE_*` / `CART_RATE_*` (burst=2); WireMock + JWT (JwtAuthFilterTest stili). Senaryolar: auth burst+1 → 429 Problem Details; limit altı ≠429; farklı XFF ayrı kota; cart aynı/farklı `sub`. Global `application.properties` 100000 dokunulmadı. Surefire `*IT` include. Docker daemon gerekir.

**Bugfix (JWT + 429):** `JwtAuthGlobalFilter` içindeki geniş `onErrorResume`, `chain.filter` sonrası rate-limit 429’unu yakalayıp 401 `INVALID_TOKEN` yapıyordu; `onErrorResume` yalnızca JWKS hatalarına daraltıldı.

Sıradaki iş: **Adım 7 — Frontend** (429 toast) veya **Adım 8 — Doğrulama & docs**.

**Dokunulan:** `api-gateway/pom.xml` (testcontainers), `RateLimitIT.java`, `JwtAuthGlobalFilter.java` (429 yutma fix)

---

## Adım 7 — Frontend (hafif, 1–2 saat) — v1’e dahil edilebilir

Durum: ✅ tamamlandı — 2026-10-12

**Ne eklendi:** `ErrorInterceptor` status 429 → toast.error (`RATE_LIMIT_EXCEEDED` / “Çok hızlı istek; biraz sonra tekrar dene”); otomatik retry yok; login’e özel UI yok (interceptor yeterli). Spec: toast + rethrow, retry yok.

Sıradaki iş: **Adım 8 — Doğrulama & dokümantasyon**.

**Dokunulan:** `error.interceptor.ts`, `error-messages.ts`, `error.interceptor.spec.ts`, `memory-bank/frontend.md`

---

## Adım 8 — Doğrulama & dokümantasyon (1–2 saat)

1. Compose: `redis` + `api-gateway` rebuild/up
2. Manuel: login’i hızlı spam → 429; normal gezinme → sorun yok
3. `docs/` veya memory-bank: rate limit tablosu + Redis bağımlılığı
4. Actuator: Redis health (gateway readiness’e bağlamak opsiyonel; v1 sonuna bırakılabilir)

---

## Günlük plan

| Gün | İş |
|-----|-----|
| **Gün 1** | Adım 0–4 (Redis + KeyResolver + route tier’ları çalışır; ham 429 OK) |
| **Gün 2** | Adım 5–8 (Problem Details, test, FE mesajı, docs, E2E smoke) |

---

## Bilinçli olarak v1 dışı (sonraya)

- nginx `limit_req` (ikinci katman)
- Admin ayrı kota
- Per-endpoint metrik dashboard
- Search/notification ghost route’ları
- Distributed “ban” / captcha
- Fail-closed prod politikası + alert

---

## Dosya dokunuş özeti

| Alan | Dosyalar |
|------|----------|
| Infra | `docker-compose.yml`, `.env.example` |
| Gateway | `pom.xml`, `application.yml`, KeyResolver’lar, 429 handler |
| Test | `api-gateway` testleri |
| FE (opsiyonel) | HTTP interceptor |
| Docs | `docs/docker.md` / memory-bank |

Downstream (`user` / `catalog` / `cart` / `order` / `payment`) **v1’de kod değişikliği yok**.

---

## Mimari notlar

- **Yazılacak yer:** `api-gateway` (+ Redis)
- **Yazılmayacak yer:** her mikroserviste ayrı rate limit
- **Neden gateway:** tek dış HTTP girişi; limit dağılmaz; brute-force catalog’a uğramadan kesilir
- **Neden Redis:** gateway birden fazla instance olunca in-memory kotası paylaşılmaz
- **Lokal Redis:** `docker compose` ile ücretsiz (`redis:7-alpine`); Cloud Redis şart değil
