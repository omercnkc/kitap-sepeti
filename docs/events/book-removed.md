# Olay: `BookRemoved`

Yayındaki bir kitap satıştan kaldırıldı (arşivlendi); artık listelenmemeli ve aramada görünmemelidir.
Kitap kaydı silinmez (sipariş geçmişi için korunur); yeniden yayınlanırsa `BookUpserted` gelir.

- **Üretici:** `catalog-service` (transactional outbox)
- **Tetikleyici:** aşağıdaki admin işlemleri başarılı olduğunda; olay, durum değişikliğiyle aynı DB transaction'ında
  `outbox` tablosuna yazılır.
- **Gönderim:** `OutboxRelay` yayınlanmamış satırları birkaç saniyede bir (`app.outbox.poll-interval`, varsayılan
  2 sn) broker'a gönderir; broker onayı (publisher confirm) gelince satır yayınlandı işaretlenir.

Stok değişiklikleri (admin stok düzeltmesi, sipariş rezervasyonları) kitabı kaldırmaz; satılabilir stok bitse bile
kitap listelenmeye devam eder ve `BookUpserted` ile `inStock: false` gelir.

## Ne zaman yayınlanır

| İşlem | Koşul |
|---|---|
| `POST /api/admin/books/{id}/archive` | Önceki durum PUBLISHED ise. |
| `DELETE /api/admin/books/{id}` | Arşivlemeyle aynı işlem (fiziksel silme yok); önceki durum PUBLISHED ise. |

Yayınlanmaz: DRAFT kitabın arşivlenmesi (hiç duyurulmamıştı) ve zaten arşivdeki kitabın tekrar arşivlenmesi.

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `book.removed` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder (ör. `book.removed` veya `book.#`).
Üretici kuyruk tanımlamaz; hiç kuyruk bağlı değilken yayınlanan mesajlar broker tarafından düşürülür.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni). Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `BookRemoved` |
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
| `bookId` | string (UUID) | Kaldırılan kitabın id'si; `aggregateId` header'ı ile aynı. |
| `occurredAt` | string (ISO-8601 UTC) | Arşivlemenin gerçekleştiği an. Kesir hanesi sayısı değişkendir (0–9). |

Örnek:

```json
{
  "eventVersion": 1,
  "bookId": "01a0f76e-4ad9-7b8c-8c88-b12e1ad81948",
  "occurredAt": "2026-10-01T12:31:02.1184529Z"
}
```

## Teslim garantisi ve consumer notları

- **At-least-once.** Aynı olay birden fazla gelebilir. Consumer **`message_id` ile idempotent** olmalıdır.
  Zaten kaldırılmış (veya hiç bilinmeyen) bir kitap için gelen `BookRemoved` hata değildir; yok sayılır.
- **Sıra garanti edilmez.** Consumer her kitap için son uyguladığı `occurredAt`'i saklamalı ve **daha eski
  `occurredAt`'li olayı yok saymalıdır** (`BookUpserted` ile ortak karşılaştırma). Kaydı tamamen silmek yerine
  `occurredAt`'i bir "silindi" işaretiyle tutmak, geç gelen eski bir `BookUpserted`'ın kitabı geri getirmesini önler.

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları
  yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**: `eventVersion` artırılır.
- Routing key veya exchange adını değiştirmek de kırıcıdır.
