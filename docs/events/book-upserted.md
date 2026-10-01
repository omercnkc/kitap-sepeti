# Olay: `BookUpserted`

Yayındaki bir kitabın aramada/listelemede gereken güncel hali. Her olay kitabın **tam anlık görüntüsüdür**;
consumer kendi kaydını bununla tamamen değiştirmelidir (alan bazlı birleştirme yok).

- **Üretici:** `catalog-service` (transactional outbox)
- **Tetikleyici:** aşağıdaki admin ve stok rezervasyonu işlemleri başarılı olduğunda; olay, kitap değişikliğiyle aynı
  DB transaction'ında `outbox` tablosuna yazılır. Biri geri alınırsa diğeri de alınır.
- **Gönderim:** `OutboxRelay` yayınlanmamış satırları birkaç saniyede bir (`app.outbox.poll-interval`, varsayılan
  2 sn) broker'a gönderir; broker onayı (publisher confirm) gelince satır yayınlandı işaretlenir.

## Ne zaman yayınlanır

| İşlem | Koşul |
|---|---|
| `POST /api/admin/books/{id}/publish` | Kitap DRAFT veya ARCHIVED'dan PUBLISHED'a geçtiğinde. Zaten yayındaysa olay yok. |
| `PATCH /api/admin/books/{id}` | Kitap PUBLISHED ise (her başarılı PATCH'te). DRAFT/ARCHIVED kitapta olay yok. |
| `POST /api/admin/books/{id}/stock-adjustments` | Kitap PUBLISHED ise ve yalnızca `inStock` değeri değiştiyse (stok 0→3 gibi). Diğer stok değişikliklerinde olay yok. |
| `POST /internal/stock/reservations` | Rezervasyon bir kitabın son satılabilir kopyalarını ayırdıysa (`inStock` true→false). Kitap başına bir olay. |
| `POST /internal/stock/reservations/{orderId}/release` | Kitap PUBLISHED ise ve rezervin geri verilmesiyle `inStock` false→true olduysa. |

Rezervasyon onayı (`.../commit`) olay üretmez: stok ve rezerv birlikte azalır, satılabilir adet değişmez.
Ayrıntılar: [catalog-internal-stock.md](../api/catalog-internal-stock.md).

Yayınlanmaz: kitap oluşturma (her zaman DRAFT), DRAFT kitapta değişiklik, arşivleme (bkz. `BookRemoved`).
Yayınevi/yazar/kategori adı veya slug'ı değiştiğinde de **yayınlanmaz**; bu durumda aramanın yeniden
indekslenmesi gerekir (bilinen eksik).

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `book.upserted` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder (ör. `book.upserted` veya `book.#`).
Üretici kuyruk tanımlamaz; hiç kuyruk bağlı değilken yayınlanan mesajlar broker tarafından düşürülür. Bu yüzden
consumer kuyruğunu durable tanımlamalı ve ilk yayından önce bind etmiş olmalıdır.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni). Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `BookUpserted` |
| `content_type` | `application/json` |
| `content_encoding` | `UTF-8` |
| `timestamp` | Olayın outbox'a yazıldığı an (`created_at`, UTC; AMQP gereği saniye hassasiyetinde) |
| `delivery_mode` | `2` (persistent) |
| header `aggregateType` | `book` |
| header `aggregateId` | Kitap id'si (UUID metni) |

## Payload

UTF-8 JSON nesnesi. Alan sırası ve boşluklar garanti edilmez (JSON olarak ayrıştırın).

| Alan | Tip | Açıklama |
|---|---|---|
| `eventVersion` | integer | Payload şema sürümü; şu an `1`. |
| `bookId` | string (UUID) | Kitap id'si; `aggregateId` header'ı ile aynı. |
| `title` | string | Başlık. |
| `isbn` | string \| null | Normalize edilmiş ISBN-10 veya ISBN-13 (tire/boşluk yok, kontrol karakteri büyük `X`). |
| `description` | string \| null | Açıklama. |
| `priceAmount` | string | Fiyat, her zaman 2 ondalık (ör. `"149.90"`). Ondalık kaybı olmasın diye metindir; sayıya çevirirken decimal tip (ör. `BigDecimal`) kullanın. HTTP yanıtlarındaki `priceAmount` sayıdır; buradaki farkın nedeni aşağıda. |
| `currency` | string | ISO 4217 kodu; şu an her zaman `TRY`. |
| `coverUrl` | string \| null | Kapak görseli (http/https). |
| `pageCount` | integer \| null | Sayfa sayısı. |
| `inStock` | boolean | Satılabilir stok (stok − rezerv) > 0. Stok miktarları payload'da yer almaz. |
| `publishedAt` | string (ISO-8601 UTC) | İlk yayın anı; arşivden yeniden yayında değişmez. |
| `publisher` | object | `{id, name, slug}`. |
| `authors` | array | `[{id, name, slug}]`, Türkçe ada göre sıralı. Boş olmaz. |
| `categories` | array | Kitabın doğrudan bağlı olduğu kategoriler, `[{id, name, slug}]`, Türkçe ada göre sıralı. Boş olmaz. |
| `categoryIdsWithAncestors` | array of string (UUID) | Kitabın kategorileri ve tüm ataları (kök dahil); kategori ağacında filtreleme için. Sıra garanti edilmez. |
| `occurredAt` | string (ISO-8601 UTC) | Değişikliğin gerçekleştiği an. Kesir hanesi sayısı değişkendir (0–9). |

Stok/rezerv miktarı, versiyon ve kitap durumu bilinçli olarak payload'da **yoktur** (olay yalnızca yayındaki
kitaplar için üretilir).

**`priceAmount` neden metin?** Payload, broker'a gönderilmeden önce outbox tablosunun MySQL `JSON` kolonunda
saklanır. MySQL bu kolonda kesirli sayıları DOUBLE'a çevirir ve sondaki sıfırları atar (`149.90` → `149.9`,
`130.00` → `130.0`). Sayı olarak yazılsaydı mesajdaki biçim HTTP yanıtlarındaki `priceAmount` ile aynı olmazdı.
Metin olarak `"149.90"` aynen korunur. Tipi değiştirmek kırıcı değişiklik olacağından (bkz. Sürümleme) bu alan
metin kalır.

Örnek:

```json
{
  "eventVersion": 1,
  "bookId": "01a0f76e-4ad9-7b8c-8c88-b12e1ad81948",
  "title": "Kırmızı Pazartesi",
  "isbn": "9786053600770",
  "description": "Bir cinayetin önceden bilinen hikâyesi.",
  "priceAmount": "149.90",
  "currency": "TRY",
  "coverUrl": "https://cdn.example.com/kapak.jpg",
  "pageCount": 180,
  "inStock": true,
  "publishedAt": "2026-10-01T12:26:36.512841Z",
  "publisher": { "id": "01920000-0000-7000-8000-000000000101", "name": "Deniz Yayınları", "slug": "deniz-yayinlari" },
  "authors": [
    { "id": "01920000-0000-7000-8000-000000000201", "name": "Ahmet Yazar", "slug": "ahmet-yazar" }
  ],
  "categories": [
    { "id": "01920000-0000-7000-8000-000000000302", "name": "Roman", "slug": "roman" }
  ],
  "categoryIdsWithAncestors": [
    "01920000-0000-7000-8000-000000000302",
    "01920000-0000-7000-8000-000000000301"
  ],
  "occurredAt": "2026-10-01T12:26:36.5304417Z"
}
```

## Teslim garantisi ve consumer notları

- **At-least-once.** Aynı olay birden fazla gelebilir. Consumer **`message_id` ile idempotent** olmalıdır
  (işlenmiş `message_id`'leri saklayıp tekrarı yok saymak).
- **Sıra garanti edilmez** (birden çok instance, yeniden deneme). Consumer her kitap için son uyguladığı
  `occurredAt`'i saklamalı ve **daha eski `occurredAt`'li olayı yok saymalıdır**. Karşılaştırma `BookUpserted` ve
  `BookRemoved` arasında ortaktır: eski bir `BookUpserted`, daha yeni bir `BookRemoved`'dan sonra gelirse kitabı
  geri getirmemelidir.
- Payload tam anlık görüntüdür; kaçırılan ara olaylar sorun değildir, en yenisi yeterlidir.

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları
  yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**: `eventVersion` artırılır.
- Routing key veya exchange adını değiştirmek de kırıcıdır.
