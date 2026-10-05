# Frontend (Kitap Sepeti UI)

## Durum (UI-10 adım 1–2, 6–7)

- Angular 13 NgModule; lazy features; proxy `/api` → Gateway 8080
- UI-10: responsive/a11y cila, production budgets, E2E checklist; Docker UI hazır
- Sırada: Notifications (UI-8); `q` arama (B1)

## Admin

| Parça | Yol | Not |
| --- | --- | --- |
| Shell | `features/admin/admin-shell/` | yan menü; lg altı NgbOffcanvas |
| Header Sepet | `layout/header/` | **Kural A:** `role===ADMIN` iken Sepet+rozet her yerde gizli; USER/misafir aynı |
| Marka logo | `assets/brand/logo.png` | header + mobil menü `<img>`; metin tekrarı yok; favicon aynı PNG |
| Header nav | `layout/header/` | `header-nav-btn` outline chip; active primary+turuncu vurgu; Sepet rozeti chip içinde |
| Publishers / Authors | `admin-*-page/` | lg tablo / dar kart; modal CRUD |
| Categories | `admin-categories-page/` | düz satır ağaç; `level` girinti + └/├ + Alt kategori rozeti + «Üst: …»; kart yok |

| Books list | `admin-books-page/` | status filter; Yayınla/Arşivle/Stok; taslak≠vitrin yardım metni |
| Book form | `admin-book-form-page/` | create/edit; lifecycle + stok; taslak/vitrin notu |
| AdminCatalogApi | `core/api/admin-catalog.api.ts` | publish/archive/stock-adjustments |
| Hata mesajları | `error-messages.ts` | STOCK_BELOW_RESERVED, BOOK_NOT_PUBLISHABLE |
## Admin kitap formu (PATCH 400)

- İstemci: `isbnValidator` (checksum), `httpUrlValidator` (mutlak http/https)
- `toUpdateBody`: boş `isbn`/`coverUrl`/`description` → `""` (temizle); dolu ISBN normalize; fiyat `roundMoney2`
- **Legacy ISBN:** seed’deki checksum’sız ISBN yüklenince validator kabul eder; PATCH’te değişmediyse `isbn` alanı **gönderilmez** (BE Bean Validation tekrarlamasın). Sarı uyarı: `hasLegacyInvalidIsbn`
- ISBN lookup: OL alanları forma yazılır; eşleşen yazar/yayınevi seçilir; eşleşmeyen → soft muted hint (error toast yok); success her zaman
- 400 `errors[]` → `fieldErrors` + interceptor toast “Girdiğiniz bilgileri kontrol edin.”
- `toCreateBody`: boş isbn/cover hiç gönderilmez
- 400 `errors[]` → `fieldErrors` + interceptor toast “Girdiğiniz bilgileri kontrol edin.”

| Parça | Yol | Not |
| --- | --- | --- |
| Modeller | `core/models/order.ts` | CheckoutRequest / OrderResponse; status + cancelled |
| OrderApi | `core/api/order.api.ts` | checkout, getById, list |
| CheckoutPage | `features/checkout/checkout-page/` | sepet özeti; adres seç/ekle; POST; 409 ORDER_PENDING_EXISTS |
| OrderDetail | `features/orders/order-detail-page/` | poll; satır/adres/toplam; 404 EmptyState |
| OrderList | `features/orders/order-list-page/` | tablo lg+ / kart dar; PaginationComponent |
| OrderStatusPipe | `shared/pipes/order-status.pipe.ts` | pending/paid/failed/cancelled Türkçe |

## Hesap

| Parça | Yol | Not |
| --- | --- | --- |
| Modeller | `core/models/user.ts` | UpdateProfile / Address OpenAPI birebir |
| AccountApi | `core/api/account.api.ts` | PATCH `/api/me`; addresses CRUD; setDefault=PATCH isDefault |
| ProfilePage | `features/account/profile-page/` | Reactive Forms; phone `""` = sil |
| AddressForm | `shared/components/address-form/` | İl→İlçe→Mahalle select; TR cep `trPhoneValidator`; checkout yeniden kullanır |
| TR geo | `core/geo/TrAddressDataService` + `assets/geo/` | lazy JSON + shareReplay; `city`/`district`/`line1` string map |
| TR phone | `shared/validators/tr-phone*` | normalize → `5xxxxxxxxx`; register/profile opsiyonel, adres zorunlu |
| AddressListPage | `features/account/addresses-page/` | modal CRUD; `/addresses/:id` 404 EmptyState |
| Header | `layout/header/` | Profil → `/account/profile`; Adreslerim → `/account/addresses` |

## Sepet

| Parça | Yol | Not |
| --- | --- | --- |
| Modeller | `core/models/cart.ts` | CartResponse / CartLineResponse OpenAPI birebir |
| CartApi | `core/api/cart.api.ts` | GET/POST/PATCH/DELETE `/api/cart…` |
| CartStore | `core/cart/cart.store.ts` | BehaviorSubject; login→load; logout→reset; count$ |
| Sepete ekle | BookList/Detail | girişsiz → login?returnUrl; toast «Sepete eklendi» |
| Header rozet | `layout/header/` | `cartCount$` badge |
| CartPage | `features/cart/cart-page/` | adet/sil/boşalt; UNAVAILABLE bandı; priceChanged/available |
| ConfirmDialog | `shared/components/confirm-dialog/` | NgbModal; sepeti boşalt onayı |
| Toast | `toast-container/` | `position-fixed bottom-0 end-0`; max-width min(360px, 90vw); header üstünü örtmez |

## Katalog UI

| Parça | Yol | Not |
| --- | --- | --- |
| BookCard | `shared/components/book-card/` | max 280px; Tükendi; addToCart emit |
| Pagination | `shared/components/pagination/` | NgbPagination; backend page 0-tabanlı; vitrin `layout=simple` (Önceki / 1/N / Sonraki) |
| BookFilters | `features/catalog/book-filters/` | kategori ağacı, fiyat, sort; lg offcanvas |
| BookListPage | `features/catalog/book-list-page/` | size=12 sabit; lg 4 kolon (~3×4); queryParams → switchMap; kapak lazy |
| BookDetailPage | `features/catalog/book-detail-page/` | getById; 404 EmptyState; sepete ekle → CartStore |

## Auth katmanı

| Parça | Yol | Not |
| --- | --- | --- |
| Modeller | `core/models/auth.ts` | OpenAPI birebir |
| AuthApi | `core/api/auth.api.ts` | register/login/refresh/getMe — Bearer yok (interceptor) |
| TokenStorage | `core/auth/token-storage.service.ts` | Access bellek; refresh rememberMe → local/session |
| AuthService | `core/auth/auth.service.ts` | `currentUser$`; logout; `storage` event (çoklu sekme) |
| APP_INITIALIZER | `core/auth/auth.initializer.ts` | refresh varsa → access + getMe; yok/hata → sessiz logout |
| AuthInterceptor | `core/interceptors/auth.interceptor.ts` | Bearer; public auth skip; 401 → tek refresh + retry |
| AuthGuard | `core/guards/auth.guard.ts` | cart/checkout/orders/account/notifications |
| GuestGuard | `core/guards/guest.guard.ts` | login/register |
| AdminGuard | `core/guards/admin.guard.ts` | canActivate + canLoad `/admin/**` |
| LoginPage / RegisterPage | `features/auth/` | Reactive Forms |

## Ortam

| Araç | Sürüm |
| --- | --- |
| Node | 14.18.1 (`.nvmrc`) |
| npm | 6.14.15 (`engines`, `frontend/.npmrc` → `engine-strict=true`) |
| Angular | 13.3.x |

Windows (nvm-windows): terminalde `nvm use 14.18.1` — Cursor agent shell PATH’e Node eklemeyebilir.

## Komutlar (`frontend/`)

```powershell
npm start          # ng serve + proxy.conf.json → :4200 (/api → localhost:8080)
npm run lint       # ng lint
npm test           # ng test (CI: --watch=false --browsers=ChromeHeadless)
npm run build      # production build → dist/frontend
```

## API / proxy

- `environment.apiUrl`: `''` — istekler göreli `/api/...`
- Geliştirme: `proxy.conf.json` → `/api` → `http://localhost:8080` (`npm start` kullanır)
- Compose UI: nginx `/api/` → `http://api-gateway:8080`

## Docker (UI)

| | |
| --- | --- |
| Dockerfile | `frontend/Dockerfile` — multi-stage `node:14.18.1-alpine` → `nginx:alpine` (non-root, :8080) |
| Context | `frontend/` (`docker compose build frontend`) |
| Compose | `frontend` servisi → `127.0.0.1:4200:8080`, `depends_on: api-gateway` (healthy) |
| SPA | `try_files $uri $uri/ /index.html` |

```powershell
# Yerel geliştirme (hot reload) — :4200
cd frontend; npm start

# Compose production UI — host :4200 (npm start kapalı olmalı)
docker compose build frontend
docker compose up -d frontend
# UI: http://127.0.0.1:4200
```

Backend stack: monorepo kökünden `docker compose up -d` (gateway + servisler). `docker compose down -v` yasak (volume silmez).

## Production budgets (`angular.json`)

| Tip | Warning | Error | Gerekçe |
| --- | --- | --- | --- |
| `initial` | **600kb** | 1mb | Bootstrap 5 + Angular 13 main ~580kb; 500kb uyarısı sürekliydi |
| `anyComponentStyle` | 2kb | 4kb | Değişmedi |

`ng build --configuration production` uyarısız geçmeli.

## UI-10 responsive / a11y notları

- `html/body`: `overflow-x: clip`; `:focus-visible` outline
- Skip link → `#main-content`
- Header brand + kullanıcı adı ellipsis (dar nav)
- `/books`: 280px tek sütun (`col-12 col-sm-6…`); filtre butonu `aria-label`
- BookCard / detay: «Sepete ekle» `aria-label` + klavye (button native Enter/Space)
- Login: label↔input; submit `aria-busy`
- Cart: kapak 56px (xs) / 72px (sm+)
- Admin shell: `min-width: 0`, safe-area padding; listeler lg tablo / dar kart

## E2E kontrol listesi (elle)

İşaretleyin: `[ ]` → `[x]`

### Ortam

- [ ] Gateway + servisler ayakta (`docker compose ps` healthy)
- [ ] **A)** `cd frontend; npm start` → http://localhost:4200 **veya**
- [ ] **B)** Compose UI: `npm start` kapalıyken `docker compose up -d frontend` → http://127.0.0.1:4200
- [ ] Port notu: host **4200** ng serve ile compose frontend arasında paylaşılamaz

### Mutlu yol (USER)

- [ ] Kayıt ol (veya mevcut USER ile giriş)
- [ ] Katalog `/books` — liste + filtre (dar: Filtreler offcanvas)
- [ ] Kitap detay → Sepete ekle (klavye: Tab → Enter)
- [ ] `/cart` — adet / sil
- [ ] Adres ekle (`/account/addresses` veya checkout’ta)
- [ ] `/checkout` → sipariş oluştur
- [ ] Mock ödeme → paid (veya polling)
- [ ] `/orders/:id` özet görünür

### Admin

- [ ] ADMIN ile giriş; header’da Admin linki
- [ ] `/admin/books/new` — yayınevi/yazar/kategori seç → oluştur (taslak)
- [ ] Yayınla → `/books` vitrinde görünür
- [ ] Arşivle → vitrinden kalkar
- [ ] Stok düzelt (opsiyonel) → Tükendi / stok güncellenir

### Compose SPA

- [ ] `/books` açılır
- [ ] Derin link `/books/<id>` yenile → 404 değil (nginx `try_files`)
- [ ] `/api/books` proxy → gateway 200
