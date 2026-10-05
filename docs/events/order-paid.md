# Olay: `OrderPaid`

Siparişin ödemesi başarıyla sonuçlandı ve sipariş `paid` durumuna geçti. Sipariş başına en fazla bir kez üretilir.

- **Üretici:** `order-service` (transactional outbox)
- **Tetikleyici:** Sipariş `pending` → `paid` geçişi (`OrderTransactions.applyPaymentSucceeded` veya `OrderTransactions.reconcilePaymentSucceeded`). Olay, durum değişikliği ve varsa `CartCheckedOut` olayıyla **aynı DB transaction'ında** `outbox` tablosuna yazılır. Biri geri alınırsa diğeri de alınır.
- **Gönderim:** `OutboxRelay` yayınlanmamış satırları birkaç saniyede bir (`app.outbox.poll-interval`, varsayılan 2 sn) broker'a gönderir; broker onayı (publisher confirm) gelince satır yayınlandı işaretlenir.

## Ne zaman yayınlanır

| Durum | Olay |
|---|---|
| Sipariş `pending` iken ödeme başarılı (`PaymentSucceeded` tüketildiğinde) | `OrderPaid` |
| Sipariş `pending` iken uzlaştırma görevinde ödemenin başarılı olduğu anlaşıldığında | `OrderPaid` |
| Sipariş zaten `paid` iken tekrar başarılı ödeme gelirse | Yok (tekrar yok sayılır) |
| Sipariş `failed` iken geç gelen başarılı ödeme (`LATE_PAYMENT_SUCCESS`) | Yok (sipariş failed kalır, admin incelemesine alınır) |

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` (user, catalog, payment, cart ile ortak) |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `order.paid` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder (ör. `order.paid` veya `order.#`). Üretici kuyruk tanımlamaz; hiç kuyruk bağlı değilken yayınlanan mesajlar broker tarafından düşürülür.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni) = payload'daki `eventId`. Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `OrderPaid` |
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
| `orderId` | string (UUID) | Evet | Ödenen siparişin id'si; `aggregateId` header'ı ile aynı. |
| `userId` | string (UUID) | Evet | Siparişi veren kullanıcının id'si. |
| `paymentId` | string (UUID) | Evet | Ödemenin id'si (payment-service). |
| `totalAmount` | string | Evet | Ödenen toplam tutar, her zaman 2 ondalık (ör. `"149.90"`). Ondalık kaybı olmasın diye metindir; sayıya çevirirken decimal tip (ör. `BigDecimal`) kullanın. |
| `currency` | string | Evet | ISO 4217 kodu (ör. `TRY`). |
| `itemCount` | integer | Evet | farklı kitap (satır) sayısı; adet toplamı değil. |
| `occurredAt` | string (ISO-8601 UTC) | Evet | Siparişin `paid` durumuna geçtiği an (`updated_at`). Kesir hanesi sayısı değişkendir (0–6). |

**`totalAmount` neden metin?** Payload, broker'a gönderilmeden önce outbox tablosunun MySQL `JSON` kolonunda saklanır. MySQL bu kolonda kesirli sayıları DOUBLE'a çevirir ve sondaki sıfırları atar (`149.90` → `149.9`). Metin olarak `"149.90"` aynen korunur.

Örnek (değerler sahte):

```json
{
  "eventId": "01920000-0000-7000-8000-00000000e001",
  "eventVersion": 1,
  "orderId": "01920000-0000-7000-8000-00000000b001",
  "userId": "01920000-0000-7000-8000-00000000a001",
  "paymentId": "01920000-0000-7000-8000-00000000f001",
  "totalAmount": "149.90",
  "currency": "TRY",
  "itemCount": 2,
  "occurredAt": "2026-10-05T10:15:30.123456Z"
}
```

## Bilinen tüketiciler

- `Notifications` (ileride: e-posta / uygulama içi bildirim gönderimi).

## Teslim garantisi ve consumer notları

- **At-least-once.** Aynı olay birden fazla gelebilir (ör. broker onayından sonra, satır yayınlandı işaretlenmeden çökme). Consumer **`message_id` / `eventId` ile idempotent** olmalıdır (veya durum makinesiyle tekrarı yutmalıdır).
- **Sıra garanti edilmez**; farklı siparişlerin olayları birbirine göre her sırada gelebilir.

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**: `eventVersion` artırılır.
- Routing key veya exchange adını değiştirmek de kırıcıdır.
