# catalog-service: internal stok rezervasyonu

Sipariş servisinin (order-service) stok ayırma, onaylama ve geri verme uçları. Yalnızca servisler arası
kullanım içindir; gateway/istemci üzerinden açılmamalıdır.

Taban yol: `/internal/stock/reservations` (catalog-service, varsayılan port `8082`).

Makine tarafından okunur sözleşme: [`catalog-service.openapi.json`](catalog-service.openapi.json) (OpenAPI 3.1,
"Internal – Stock" tag'i; çalışan serviste `/v3/api-docs` ve `/swagger-ui.html`). Dosya `OpenApiContractTest` ile
koda karşı denetlenir; güncellemek için:
`.\mvnw.cmd -pl catalog-service test "-Dtest=OpenApiContractTest" "-Dopenapi.contract.update=true"`.

## Kimlik doğrulama

Her istekte servis anahtarı başlıkta gönderilir:

```
X-Internal-Api-Key: <ham anahtar>
```

- Kullanıcı JWT'si (`Authorization: Bearer`, ADMIN dahil) bu uçlarda **geçersizdir**; yok sayılır.
- Başlık yok, boş veya anahtar yanlışsa: `401`, `code = UNAUTHORIZED`, `WWW-Authenticate: ApiKey realm="internal"`.
  Yanlış anahtar ile eksik anahtar ayırt edilmez.
- catalog-service ham anahtarı saklamaz; yalnızca SHA-256 özetini (64 karakter hex) bilir:

  | Ortam değişkeni | Nerede | İçerik |
  |---|---|---|
  | `ORDER_INTERNAL_API_KEY` | order-service | Ham anahtar (rastgele 32 bayt, base64url) |
  | `CATALOG_INTERNAL_KEY_ORDER_SHA256` | catalog-service | Ham anahtarın UTF-8 baytlarının SHA-256 özeti, hex |

  Özet boşsa istemci kapalıdır (uygulama yine açılır, istekler 401 alır). Dolu ama 64 karakter hex değilse
  catalog-service açılmaz (hata mesajı değeri içermez).
- Anahtar değiştirmek: yeni anahtar üret, iki değişkeni birlikte güncelle, iki servisi yeniden başlat.

## Kurallar

- **Ya hep ya hiç.** Bir siparişin tüm kalemleri tek transaction'da ayrılır; biri bile başarısızsa hiçbiri ayrılmaz.
- Stok kontrolü tek koşullu UPDATE ile yapılır (yalnızca yayındaki kitap ve `stok − rezerv ≥ adet`); eşzamanlı
  isteklerde fazla satış olmaz.
- Rezervasyon süresi `app.stock.reservation-ttl` (varsayılan 15 dk): `expiresAt = şimdi + süre`. Süresi dolan
  rezervasyonlar arka planda serbest bırakılır; bkz. [Süre dolumu](#süre-dolumu).
- Kalemler yanıtta ve işlemde `bookId`'ye göre sıralıdır (istekteki sıra önemsizdir).
- Yayındaki bir kitabın `inStock` değeri değişirse aynı transaction'da `BookUpserted` olayı yazılır
  (bkz. [book-upserted.md](../events/book-upserted.md)). Onay olay üretmez.

## Rezervasyon nesnesi

```json
{
  "orderId": "4a3b1f0e-6a52-4c39-9a7b-2d1c1e0f9b11",
  "status": "held",
  "expiresAt": "2026-10-01T13:55:00.123456Z",
  "items": [
    {
      "bookId": "01920000-0000-7000-8000-000000000401",
      "title": "Kırmızı Pazartesi",
      "quantity": 2,
      "unitPrice": 149.90,
      "currency": "TRY"
    }
  ]
}
```

| Alan | Açıklama |
|---|---|
| `status` | `held` (ayrıldı), `committed` (stoktan düşüldü), `released` (geri verildi). |
| `expiresAt` | `held` rezervasyonun geçerlilik sonu (ISO-8601 UTC). Durum değişse de aynı kalır. |
| `unitPrice` | Kitabın **okunduğu andaki** fiyatı; kitap yanıtlarındaki `priceAmount` ile aynı biçimde JSON sayısı, her zaman 2 ondalık (`130` → `130.00`). İstemci kayan nokta yerine ondalık tiple (ör. `BigDecimal`) okumalı. Yeni rezervasyonda rezervasyon anının fiyatıdır; mevcut rezervasyon döndürülürken (idempotent tekrar, GET, commit, release) güncel fiyat okunur. Fiyatın sipariş için anlık görüntüsünü saklamak **order-service'in sorumluluğundadır**. |

## Uçlar

### `POST /internal/stock/reservations` — stok ayır

İstek:

```json
{
  "orderId": "4a3b1f0e-6a52-4c39-9a7b-2d1c1e0f9b11",
  "items": [
    { "bookId": "01920000-0000-7000-8000-000000000401", "quantity": 2 },
    { "bookId": "01920000-0000-7000-8000-000000000402", "quantity": 1 }
  ]
}
```

- `orderId` zorunlu (UUID). `items` 1–50 kalem; `quantity` 1–100; aynı `bookId` iki kez geçemez.
- **Idempotency** (`orderId` anahtarı):
  - Sipariş için rezervasyon yoksa oluşturulur: `201 Created`, `Location: /internal/stock/reservations/{orderId}`.
  - Varsa ve istekteki (bookId, adet) kümesi aynıysa hiçbir şey değişmez: `200 OK` + mevcut hal (durum `committed`
    veya `released` olsa bile).
  - Varsa ama küme farklıysa: `409 RESERVATION_MISMATCH`.
  - Aynı sipariş aynı anda birden çok kez gönderilirse yalnızca biri `201` alır, diğerleri `200` + aynı rezervasyon.

Yetersiz stok örneği (`409`):

```json
{
  "detail": "Not enough stock for one or more books.",
  "instance": "/internal/stock/reservations",
  "status": 409,
  "title": "Conflict",
  "code": "INSUFFICIENT_STOCK",
  "bookIds": ["01920000-0000-7000-8000-000000000402"]
}
```

`bookIds`: hatanın kodu ile eşleşen kitaplar. Tüm kalemler denenir, ilk hatada durulmaz. Hem satışta olmayan hem
stoğu yetmeyen kitap varsa kod `BOOK_NOT_AVAILABLE` olur ve `bookIds` yalnızca satışta olmayanları içerir.

### `POST /internal/stock/reservations/{orderId}/commit` — onayla (stoktan düş)

- `held` kalemler için stok ve rezerv adet kadar azalır; durum `committed`. `200` + hal.
- Süresi geçmiş `held` rezervasyon da onaylanır.
- Zaten tümü `committed` ise değişiklik yok, `200`.
- Bir kalem `released` ise `409 RESERVATION_RELEASED`. Rezervasyon yoksa `404 RESOURCE_NOT_FOUND`.

### `POST /internal/stock/reservations/{orderId}/release` — geri ver

- `held` kalemlerin rezervi geri verilir; durum `released`. `200` + hal.
- Zaten tümü `released` ise değişiklik yok, `200`.
- Bir kalem `committed` ise `409 RESERVATION_COMMITTED`. Rezervasyon yoksa `404 RESOURCE_NOT_FOUND`.

### `GET /internal/stock/reservations/{orderId}` — oku

`200` + hal; yoksa `404 RESOURCE_NOT_FOUND`.

## Süre dolumu

- Rezervasyon süresi (TTL) **15 dakikadır**. `held` rezervasyon `expiresAt`'e kadar onaylanmazsa catalog-service onu
  kendisi serbest bırakır: rezerv geri verilir, durum `released` olur — `release` ucunun yaptığının aynısı (yayındaki
  kitabın `inStock` değeri değişirse `BookUpserted` yazılır).
- Bunu yapan görev yaklaşık **30 saniyede bir** çalışır (`app.stock.expiry.interval`, tur başına en fazla
  `app.stock.expiry.batch-size` = 100 sipariş). Bu yüzden serbest bırakma TTL'den **biraz sonra** olur; `expiresAt`
  kesin bir kesim anı değildir.
- Süresi geçmiş ama görev henüz dokunmamış (`held`) bir rezervasyon **hâlâ onaylanabilir** (`commit` → `200`).
- Görev önce davranırsa `commit` → `409 RESERVATION_RELEASED` döner. Bu durumda order-service ödemeyi **iade ederek**
  telafi etmelidir (stok başkasına ayrılmış olabilir; aynı siparişle yeniden rezervasyon `200` + `released` hal döner).
- O an onaylanan veya iptal edilen bir sipariş görev tarafından beklenmeden atlanır ve sonraki turda yeniden
  değerlendirilir; aynı siparişte onay ile süre dolumundan **yalnızca biri** kazanır.
- Ayrı bir "süre doldu" olayı **yoktur**. order-service durumu öğrenmek için
  `GET /internal/stock/reservations/{orderId}` ucunu sorgulayabilir (`status: "released"`).

## Hata kodları

Tüm hatalar RFC 9457 ProblemDetail (`application/problem+json`) ve `code` alanı taşır.

| HTTP | `code` | Ne zaman |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Gövde kurallara uymuyor; `errors[{field, message}]` (ör. `items`, `items[0].quantity`). |
| 400 | `MALFORMED_REQUEST` | JSON okunamıyor / UUID biçimi hatalı. |
| 401 | `UNAUTHORIZED` | Servis anahtarı yok veya yanlış. |
| 404 | `RESOURCE_NOT_FOUND` | Siparişin rezervasyonu yok. |
| 409 | `INSUFFICIENT_STOCK` | Satılabilir stok yetmiyor; `bookIds`. |
| 409 | `BOOK_NOT_AVAILABLE` | Kitap yok veya yayında değil (taslak/arşiv); `bookIds`. |
| 409 | `RESERVATION_MISMATCH` | Sipariş için farklı kalemlerle rezervasyon zaten var. |
| 409 | `RESERVATION_RELEASED` | Geri verilmiş rezervasyon onaylanamaz. |
| 409 | `RESERVATION_COMMITTED` | Onaylanmış rezervasyon geri verilemez. |
| 500 | `INTERNAL_ERROR` | Beklenmeyen hata (ör. rezerv/stok tutarsızlığı). İşlem geri alınmıştır; aynı istek güvenle tekrarlanabilir. |

## Örnek akış (PowerShell)

```powershell
$h = @{ "X-Internal-Api-Key" = $env:ORDER_INTERNAL_API_KEY }
$order = [guid]::NewGuid()
$body = @{ orderId = $order; items = @(@{ bookId = "01920000-0000-7000-8000-000000000401"; quantity = 1 }) } | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri http://localhost:8082/internal/stock/reservations -Headers $h -ContentType application/json -Body $body
Invoke-RestMethod -Method Post -Uri "http://localhost:8082/internal/stock/reservations/$order/commit" -Headers $h
```
