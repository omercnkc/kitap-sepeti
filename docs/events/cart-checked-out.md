# Olay: `CartCheckedOut`

Sipariş başarıyla ödendiğinde (`paid` durumuna geçtiğinde), ilgili sepetin kapatılması için yayınlanır.

- **Üretici:** `order-service` (transactional outbox)
- **Tetikleyici:** Siparişin `paid` durumuna geçişi (`OrderTransactions.applyPaymentSucceeded` veya `OrderTransactions.reconcilePaymentSucceeded`). `OrderPaid` olayı ile **aynı DB transaction'ında** `outbox` tablosuna yazılır.
- **Gönderim:** `OutboxRelay` yayınlanmamış satırları birkaç saniyede bir (`app.outbox.poll-interval`, varsayılan 2 sn) broker'a gönderir; broker onayı (publisher confirm) gelince satır yayınlandı işaretlenir.

## Ne zaman yayınlanır

| Durum | Olay |
|---|---|
| Sipariş `pending` iken ödeme başarılı olduğunda (`paid` geçişi) | `CartCheckedOut` |
| Sipariş uzlaştırma turunda ödendiğinde (`paid` geçişi) | `CartCheckedOut` |
| Sipariş zaten `paid` iken tekrar başarılı ödeme gelirse | Yok (tekrar yok sayılır) |
| Sipariş `failed` olursa | Yok (sepet aktif kalır) |

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` (user, catalog, payment, cart ile ortak) |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `cart.checked-out` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni) = payload'daki `eventId`. Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `CartCheckedOut` |
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
| `cartId` | string (UUID) | Evet | Kapatılacak sepetin id'si. |
| `userId` | string (UUID) | Evet | Sepetin sahibi olan kullanıcının id'si. |
| `orderId` | string (UUID) | Evet | Bu sepetten oluşturulan siparişin id'si. |
| `occurredAt` | string (ISO-8601 UTC) | Evet | Siparişin `paid` durumuna geçtiği an (`updated_at`). Kesir hanesi sayısı değişkendir (0–6). |

Örnek (değerler sahte):

```json
{
  "eventId": "01920000-0000-7000-8000-00000000e003",
  "eventVersion": 1,
  "cartId": "01920000-0000-7000-8000-00000000c001",
  "userId": "01920000-0000-7000-8000-00000000a003",
  "orderId": "01920000-0000-7000-8000-00000000b003",
  "occurredAt": "2026-10-05T10:15:32.789012Z"
}
```

## Bilinen tüketiciler

- `cart-service`: `CartCheckedOutListener` (`cart.checkouts` kuyruğu). Sepet durumunu `active` → `checked_out` yapar; böylece kullanıcının sonraki ürün ekleme isteğinde yeni sepet açılır.

## Teslim garantisi ve consumer notları

- **At-least-once.** Aynı olay birden fazla gelebilir. Consumer sepet zaten `checked_out` ise işlemi sessizce yok saymalıdır (idempotent geçiş).
- **Sıra garanti edilmez.**

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**: `eventVersion` artırılır.
- Routing key veya exchange adını değiştirmek de kırıcıdır.
