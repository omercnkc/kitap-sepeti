# KitapSepeti

Mikroservis mimarili bir kitap e-ticareti: keşfet, sepete ekle, öde, siparişini takip et.

Java 21 / Spring Boot backend, Angular vitrin ve Docker Compose ile uçtan uca yerel ortam — JWT kimlik, stok rezervasyonu, outbox olayları ve mock ödeme akışı hazır.

---

## Ne sunuyor?

| Alan | Durum |
|------|--------|
| Katalog (arama, kategori, admin, ISBN lookup) | Hazır |
| Sepet (canlı fiyat/stok, Catalog CB) | Hazır |
| Ödeme (mock webhook, outbox → RabbitMQ) | Hazır |
| Sipariş (checkout, telafi, payment consumer) | Aktif geliştirme |
| API Gateway + Angular UI | Hazır |

---

## Mimari

```
Tarayıcı → frontend (:4200)
              ↓ /api
         api-gateway (:8080)
              ├── user-service      (:8081)  kimlik, profil, adres
              ├── catalog-service   (:8082)  kitap, stok, admin
              ├── cart-service      (:8083)  sepet
              ├── payment-service   (:8087)  ödeme (internal + webhook)
              └── order-service     (:8088)  checkout / sipariş
                     ↕
              MySQL 8.4 · RabbitMQ 4
```

Servisler kendi şemasına yazar (`user_db`, `catalog_db`, …). Olaylar **transactional outbox** ile RabbitMQ’ya çıkar. Servisler arası güvenilir çağrılar: JWT (kullanıcı), API key + SHA-256 özeti (internal), HMAC (ödeme webhook).

Ortak hata/güvenlik kodu `common` kütüphanesinde; sözleşmeler `docs/api/` ve `docs/events/` altında.

---

## Teknoloji

| Katman | Seçim |
|--------|--------|
| Backend | Java 21, Spring Boot 4.1, Spring Security, Data JPA, Flyway, OpenFeign, Resilience4j |
| Mesajlaşma | RabbitMQ 4 (management UI) |
| Veri | MySQL 8.4 (`utf8mb4`) |
| Gateway | Spring Cloud Gateway |
| Frontend | Angular 13, Bootstrap 5 / ng-bootstrap |
| Çalıştırma | Docker Compose, Maven Wrapper (`mvnw`) |

---

## Hızlı başlangıç

**Gereksinimler:** Docker Desktop, Git. (İsteğe bağlı: JDK 21, Node 14.18.1 — yerelde `spring-boot:run` / `ng serve` için.)

### 1. Ortam dosyası

```powershell
copy .env.example .env
```

`.env` içindeki boş alanları doldurun. Önemli noktalar:

- DB parolaları yalnızca **harf ve rakam** (MySQL init script’leri SQL’e gömer).
- `ORDER_INTERNAL_API_KEY` rastgele üretin; `*_INTERNAL_KEY_ORDER_SHA256` alanları bu anahtarın **SHA-256 hex** özeti olmalı (64 karakter).
- `PAYMENT_MOCK_WEBHOOK_SECRET` ≥ 32 karakter.
- JWT anahtar yolları: yerelde `secrets/` altındaki dosyalara işaret edin (compose container içinde secret mount kullanır).

Ayrıntılı Docker notları: [`docs/docker.md`](docs/docker.md).

### 2. Tüm yığını ayağa kaldırın

```powershell
docker compose build
docker compose up -d
docker compose ps
```

| Adres | Ne |
|-------|-----|
| http://127.0.0.1:4200 | Vitrin (Angular) |
| http://localhost:8080 | API Gateway |
| http://localhost:15672 | RabbitMQ Management |
| http://localhost:8090 | Adminer (`--profile tools`) |

Host portları çoğunlukla `127.0.0.1` ile sınırlıdır (LAN’dan kapalı).

> `docker compose down -v` volume’ları (DB + kuyruklar) siler. Durdurmak için: `docker compose stop`.

### 3. Sağlık kontrolü

```powershell
curl.exe -s http://localhost:8080/actuator/health
curl.exe -s http://localhost:8082/actuator/health/readiness
```

Swagger örnekleri: catalog `http://localhost:8082/swagger-ui.html`, cart `:8083`, payment `:8087`, order `:8088`.

---

## Yerel geliştirme

Compose’da bir servis çalışırken aynı porta `spring-boot:run` bağlanmaz — önce ilgili container’ı durdurun.

```powershell
# Ortak kütüphane (ilk sefer / common değişince)
.\mvnw.cmd -pl common -am install -DskipTests

# Tek servis
.\mvnw.cmd -pl catalog-service spring-boot:run
.\mvnw.cmd -pl cart-service -am test

# UI (gateway :8080 ayakta olmalı)
cd frontend
npm ci
npm start
# → http://localhost:4200  |  proxy: /api → localhost:8080
```

Catalog yerel profilde (`local`) Open Library kapaklı **dev seed** yükler; container imajında bu profil kapalıdır.

---

## Repo yapısı

```
kitapSepeti/
├── api-gateway/          # Tek giriş noktası
├── user-service/
├── catalog-service/
├── cart-service/
├── payment-service/
├── order-service/
├── common/               # Paylaşılan kütüphane (çalıştırılmaz)
├── frontend/             # Angular vitrin + admin
├── docs/api/             # OpenAPI sözleşmeleri
├── docs/events/          # Domain olay sözleşmeleri
├── docs/docker.md        # Compose / port / env ayrıntısı
├── infra/mysql/init/     # Şema + kullanıcı bootstrap
├── memory-bank/          # Proje kararları ve bağlam
├── docker-compose.yml
└── .env.example
```

---

## Dokümantasyon

| Konu | Yer |
|------|-----|
| Docker, portlar, env | [`docs/docker.md`](docs/docker.md) |
| Olay sözleşmeleri | [`docs/events/`](docs/events/) |
| OpenAPI JSON | [`docs/api/`](docs/api/) |
| Mimari kararlar | [`memory-bank/systemPatterns.md`](memory-bank/systemPatterns.md) |
| Frontend notları | [`memory-bank/frontend.md`](memory-bank/frontend.md) |

---

## Katkı ve durum

- Ana branch: **`main`** — [github.com/omercnkc/kitap-sepeti](https://github.com/omercnkc/kitap-sepeti)
- Küçük, odaklı PR’lar; secret / `.env` commit edilmez.
- Bildirim servisi v1 planında uygulama içi bildirimlerle sınırlıdır (e-posta yok).

Sorun veya fikriniz için issue açın; yerel kurulum takılırsa önce `.env` zorunlu alanlarını ve `docker compose ps` health durumunu kontrol edin.
