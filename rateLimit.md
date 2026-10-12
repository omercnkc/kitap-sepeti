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

Adım 0–3 tamam; sıradaki: **Adım 4 — Route bazlı limitler**.

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

Sıradaki iş: **Adım 4 — Route bazlı limitler** (`#{@ipKeyResolver}` / `#{@userOrIpKeyResolver}` + tier tablosu).

**Dokunulan:** `api-gateway/.../ratelimit/`, unit test

---

## Adım 4 — Route bazlı limitler (2–3 saat)

1. Mevcut route listesini **böl** veya aynı URI’ye birden fazla route + `order` ile önceliklendir:
   - `auth` (sıkı, IP)
   - `catalog-public` (orta, IP) — mümkünse yalnızca GET; admin ayrı kalsın
   - `cart` (user)
   - `orders` / checkout (sıkı, user)
   - `payment-webhook` (IP)
   - `default` catch-all (gevşek)
2. Her route’a `RequestRateLimiter` filter + `redis-rate-limiter.replenishRate` / `burstCapacity` / `requestedTokens`
3. Değerleri `app.rate-limit.*` altında topla; env ile override (`AUTH_RATE_REPLENISH` vb.)

**Dokunulan:** `application.yml` (asıl iş burası)

---

## Adım 5 — 429 yanıtı (2–3 saat)

1. Gateway default 429’u mevcut **Problem Details** stiline çek
   (`type`/`title`/`status`/`detail`/`code` — diğer 401’lerle uyumlu)
2. Header: `Retry-After` (saniye)
3. `X-Request-Id` korunsun (zaten global filter var)
4. Opsiyonel: `RateLimitExceeded` benzeri bir `code` (gateway’e özel; common’a zorla taşımak şart değil)

**Dokunulan:** küçük bir `WebExceptionHandler` / filter customizer

---

## Adım 6 — Testler (3–4 saat)

1. Testcontainers Redis (veya embedded) ile gateway testi
2. Senaryolar:
   - auth: N+1. istek → **429**
   - limit altı → **200/401** (normal akış bozulmasın)
   - farklı IP’ler birbirini etkilemesin
   - JWT’li cart: aynı user kotası paylaşsın
3. Mevcut JWT/CORS/route testlerinin kırılmadığını doğrula
   `.\mvnw.cmd -pl api-gateway -am test`

**Dokunulan:** `api-gateway/src/test/...`

---

## Adım 7 — Frontend (hafif, 1–2 saat) — v1’e dahil edilebilir

1. HTTP interceptor: **429** → kısa Türkçe toast/snackbar (“Çok hızlı istek; biraz sonra tekrar dene”)
2. Login ekranında özel mesaj (brute-force hissi vermeden)
3. Otomatik retry **yapma** (auth’ta tehlikeli)

Workspace kuralı: yalnızca `frontend/` + gerekirse `memory-bank/frontend.md`

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
