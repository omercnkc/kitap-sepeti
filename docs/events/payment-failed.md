# Olay: `PaymentFailed`

Siparişin ödemesi başarısız sonuçlandı (ör. kart reddedildi). Sipariş başına en fazla bir ödeme vardır; bir ödeme için
bu olay ya da [`PaymentSucceeded`](payment-succeeded.md) **yalnızca bir kez** üretilir.

- **Üretici:** `payment-service` (transactional outbox)
- **Tetikleyici:** ödeme `initiated` → `failed` geçişi (`PaymentResults.recordFailed`). Olay, durum değişikliğiyle
  aynı DB transaction'ında `outbox` tablosuna yazılır. Biri geri alınırsa diğeri de alınır.
- **Gönderim:** `OutboxRelay` yayınlanmamış satırları birkaç saniyede bir (`app.outbox.poll-interval`, varsayılan
  2 sn) broker'a gönderir; broker onayı (publisher confirm) gelince satır yayınlandı işaretlenir.

## Ne zaman yayınlanır

| Durum | Olay |
|---|---|
| Ödeme `initiated` iken başarısız sonuç | `PaymentFailed` |
| Ödeme zaten aynı kodla `failed` iken başarısız sonuç (tekrar) | Yok |
| Ödeme farklı kodla `failed` ya da `succeeded` iken başarısız sonuç (çelişen sonuç) | Yok; ödeme değişmez, servis WARN yazar |

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` (user-service ve catalog-service ile ortak) |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `payment.failed` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder (ör. `payment.failed` veya `payment.#`).
Üretici kuyruk tanımlamaz; hiç kuyruk bağlı değilken yayınlanan mesajlar broker tarafından düşürülür.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni) = payload'daki `eventId`. Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `PaymentFailed` |
| `content_type` | `application/json` |
| `content_encoding` | `UTF-8` |
| `timestamp` | Olayın outbox'a yazıldığı an (`created_at`, UTC; AMQP gereği saniye hassasiyetinde) |
| `delivery_mode` | `2` (persistent) |
| header `aggregateType` | `payment` |
| header `aggregateId` | Ödeme id'si (UUID metni) |

## Payload

UTF-8 JSON nesnesi. Alan sırası ve boşluklar garanti edilmez (JSON olarak ayrıştırın).

| Alan | Tip | Açıklama |
|---|---|---|
| `eventVersion` | integer | Payload şema sürümü; şu an `1`. |
| `eventId` | string (UUID) | Olay id'si; `message_id` ile aynı. Tekrar ayıklamada kullanılır. |
| `paymentId` | string (UUID) | Ödeme id'si; `aggregateId` header'ı ile aynı. |
| `orderId` | string (UUID) | Ödemenin ait olduğu sipariş (order-service). |
| `amount` | string | Ödemenin tutarı, her zaman 2 ondalık (ör. `"149.99"`). Metindir (bkz. [`PaymentSucceeded`](payment-succeeded.md#payload)); decimal tiple ayrıştırın. |
| `currency` | string | ISO 4217 kodu (ör. `TRY`). |
| `failureCode` | string | Başarısızlık nedeni, `^[A-Z][A-Z0-9_]{0,63}$` (ör. `CARD_DECLINED`). Hiçbir zaman null değil. Yeni kodlar eklenebilir; consumer bilinmeyen kodu genel başarısızlık saymalıdır. |
| `occurredAt` | string (ISO-8601 UTC) | Ödemenin `failed` olduğu an (ödeme kaydının `updated_at`'i). Kesir hanesi sayısı değişkendir (0–6). |

Kullanıcı id'si, sağlayıcı ve sağlayıcı referansı bilinçli olarak payload'da **yoktur**.

Örnek (değerler sahte):

```json
{
  "eventVersion": 1,
  "eventId": "01920000-0000-7000-8000-00000000e002",
  "paymentId": "01920000-0000-7000-8000-00000000a002",
  "orderId": "01920000-0000-7000-8000-00000000b002",
  "amount": "149.99",
  "currency": "TRY",
  "failureCode": "CARD_DECLINED",
  "occurredAt": "2026-10-04T10:15:31.654321Z"
}
```

## Teslim garantisi ve consumer notları

- **At-least-once.** Aynı olay birden fazla gelebilir. Consumer **`message_id` / `eventId` ile idempotent** olmalıdır
  (işlenmiş id'leri saklayıp tekrarı yok saymak).
- Bir ödeme için tek sonuç olayı üretilir; consumer siparişin zaten sonuçlanmış olduğunu görürse olayı yok saymalıdır.
- **Sıra garanti edilmez**; farklı ödemelerin olayları birbirine göre her sırada gelebilir.

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**: `eventVersion` artırılır.
- Routing key veya exchange adını değiştirmek de kırıcıdır.
