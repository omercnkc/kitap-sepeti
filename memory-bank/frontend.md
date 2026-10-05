# Frontend (Kitap Sepeti UI)

## Durum (UI-9 tamam — adım 7–9)

- Angular 13 NgModule; lazy features; proxy `/api` → Gateway 8080
- UI-9.7–9: publish / archive / stock-adjust + dar ekran kart listeler
- Sırada: UI-10 Docker; `q` arama (B1); Notifications

## Admin

| Parça | Yol | Not |
| --- | --- | --- |
| Shell | `features/admin/admin-shell/` | yan menü; lg altı NgbOffcanvas |
| Publishers / Authors | `admin-*-page/` | lg tablo / dar kart; modal CRUD |
| Categories | `admin-categories-page/` | ağaç; parent taşıma; sil |
| Books list | `admin-books-page/` | status filter; Yayınla/Arşivle/Stok |
| Book form | `admin-book-form-page/` | create/edit; lifecycle + stok modal |
| AdminCatalogApi | `core/api/admin-catalog.api.ts` | publish/archive/stock-adjustments |
| Hata mesajları | `error-messages.ts` | STOCK_BELOW_RESERVED, BOOK_NOT_PUBLISHABLE |
| Guard | `admin.guard.ts` | canActivate + canLoad → `/books` |

## Sipariş / Checkout

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
| AddressForm | `shared/components/address-form/` | sunum; checkout yeniden kullanır |
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

## Katalog UI

| Parça | Yol | Not |
| --- | --- | --- |
| BookCard | `shared/components/book-card/` | max 280px; Tükendi; addToCart emit |
| Pagination | `shared/components/pagination/` | NgbPagination; backend page 0-tabanlı |
| BookFilters | `features/catalog/book-filters/` | kategori ağacı, fiyat, sort; lg offcanvas |
| BookListPage | `features/catalog/book-list-page/` | queryParams → switchMap → CatalogApi.list |
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
