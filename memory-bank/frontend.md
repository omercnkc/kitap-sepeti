# Frontend (Kitap Sepeti UI)

## Durum (UI-4 Adım 1–5)

- Angular 13 NgModule; lazy features; proxy `/api` → Gateway 8080
- UI-3 auth katmanı tamam (interceptor, guard, login/register)
- UI-4.1–5: BookCard, BookList + filtreler (URL query), Pagination, BookDetailPage
- Sırada: UI-5 Sepet / `q` arama (B1)

## Katalog UI

| Parça | Yol | Not |
| --- | --- | --- |
| BookCard | `shared/components/book-card/` | max 280px; Tükendi; addToCart emit (Cart UI-5) |
| Pagination | `shared/components/pagination/` | NgbPagination; backend page 0-tabanlı |
| BookFilters | `features/catalog/book-filters/` | kategori ağacı, fiyat, sort; lg offcanvas |
| BookListPage | `features/catalog/book-list-page/` | queryParams → switchMap → CatalogApi.list |
| BookDetailPage | `features/catalog/book-detail-page/` | getById; 404 EmptyState; sepete ekle TODO |

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
npm start          # ng serve + proxy.conf.json → :4200
npm run lint       # ng lint
npm test           # ng test (CI: --watch=false --browsers=ChromeHeadless)
npm run build      # production build
```

## Klasör özeti

```text
frontend/src/app/
├── core/           # tekil servisler (CoreModule.forRoot)
├── shared/         # SharedModule (CommonModule, Router, NgbModule)
├── layout/         # Shell, Header, Footer, NotFound
└── features/       # lazy modüller (auth, catalog, cart, …)
```

## API / proxy

- `environment.apiUrl`: `''` — geliştirmede istekler göreli `/api/...`
- `proxy.conf.json`: `/api` → `http://localhost:8080`
- Backend stack yalnızca monorepo kökünden (`docker compose`); UI agent compose çalıştırmaz

## Docker notu

- Gateway + mikroservisler: ana klasörde `docker compose up -d`
- UI: `npm start` ile yerel 4200 (Docker dışı)
