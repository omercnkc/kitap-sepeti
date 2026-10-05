# Olay: `OrderFailed`

Sipariş başarısızlıkla sonuçlandı ve `failed` durumuna geçti. Sipariş başına en fazla bir kez üretilir.

- **Üretici:** `order-service` (transactional outbox)
- **Tetikleyici:** Sipariş `pending` → `failed` geçişi (`OrderTransactions.applyPaymentFailed`, `OrderTransactions.reconcilePaymentFailed`, `CheckoutService` veya `PendingReconciliationJob`). Olay, durum değişikliğiyle **aynı DB transaction'ında** `outbox` tablosuna yazılır.
- **Gönderim:** `OutboxRelay` yayınlanmamış satırları birkaç saniyede bir (`app.outbox.poll-interval`, varsayılan 2 sn) broker'a gönderir; broker onayı (publisher confirm) gelince satır yayınlandı işaretlenir.

## Ne zaman yayınlanır

| Durum | Olay |
|---|---|
| Sipariş `pending` iken ödeme başarısız olursa (`PaymentFailed` tüketildiğinde) | `OrderFailed` |
| Sipariş `pending` iken uzlaştırma görevinde ödemenin başarısız olduğu anlaşıldığında | `OrderFailed` |
| Checkout sırasında stok rezervasyonu başarısız olduğunda (`OUT_OF_STOCK`, `BOOK_NOT_AVAILABLE`) | `OrderFailed` |
| Bekleyen sipariş uzlaştırma zaman aşımına uğradığında (`ORDER_EXPIRED`) | `OrderFailed` |
| Checkout yarıda kesildiğinde (`CHECKOUT_INTERRUPTED`) | `OrderFailed` |
| Sipariş zaten `failed` iken tekrar başarısızlık sonucu gelirse | Yok (tekrar yok sayılır) |

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` (user, catalog, payment, cart ile ortak) |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `order.failed` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder (ör. `order.failed` veya `order.#`). Üretici kuyruk tanımlamaz; hiç kuyruk bağlı değilken yayınlanan mesajlar broker tarafından düşürülür.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni) = payload'daki `eventId`. Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `OrderFailed` |
| `content_type` | `application/json` |
| `content_encoding` | `UTF-8` |
| `timestamp` | Olayın outbox'a yazıldığı an (`created_at`, UTC; AMQP gereği saniye hassasiyetinde) |
| `delivery_mode` | `2` (persistent) |
| header `aggregateType` | `order` |
| header `aggregateId` | Sipariş id'si (UUID metni) |

## Payload

UTF-8 JSON nesnesi. Alan sırası ve boşluklar garanti edilmez (JSON olarak ayrıştırın).

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `eventId` | string (UUID) | Evet | Olay id'si; `message_id` ile aynı. Tekrar ayıklamada kullanılır. |
| `eventVersion` | integer | Evet | Payload şema sürümü; şu an `1`. |
| `orderId` | string (UUID) | Evet | Başarısız olan siparişin id'si; `aggregateId` header'ı ile aynı. |
| `userId` | string (UUID) | Evet | Siparişi veren kullanıcının id'si. |
| `failureCode` | string | Evet | Başarısızlık nedeni (ör. `OUT_OF_STOCK`, `CARD_DECLINED`, `ORDER_EXPIRED`, `CHECKOUT_INTERRUPTED`). Hiçbir zaman null değildir. |
| `occurredAt` | string (ISO-8601 UTC) | Evet | Siparişin `failed` durumuna geçtiği an (`updated_at`). Kesir hanesi sayısı değişkendir (0–6). |

Örnek (değerler sahte):

```json
{
  "eventId": "01920000-0000-7000-8000-00000000e002",
  "eventVersion": 1,
  "orderId": "01920000-0000-7000-8000-00000000b002",
  "userId": "01920000-0000-7000-8000-00000000a002",
  "failureCode": "CARD_DECLINED",
  "occurredAt": "2026-10-05T10:15:31.654321Z"
}
```

## Bilinen tüketiciler

- `Notifications` (ileride: kullanıcıya siparişin başarısız olduğunu bildirmek için).

## Teslim garantisi ve consumer notları

- **At-least-once.** Aynı olay birden fazla gelebilir. Consumer **`message_id` / `eventId` ile idempotent** olmalıdır (veya durum makinesiyle tekrarı yutmalıdır).
- **Sıra garanti edilmez**; farklı siparişlerin olayları birbirine göre her sırada gelebilir.

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**: `eventVersion` artırılır.
- Routing key veya exchange adını değiştirmek de kırıcıdır.
