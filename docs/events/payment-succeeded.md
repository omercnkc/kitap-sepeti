# Olay: `PaymentSucceeded`

Siparişin ödemesi başarıyla sonuçlandı. Sipariş başına en fazla bir ödeme vardır; bir ödeme için bu olay ya da
[`PaymentFailed`](payment-failed.md) **yalnızca bir kez** üretilir (aynı sonuç tekrar gelirse ya da çelişen bir sonuç
gelirse olay yok).

- **Üretici:** `payment-service` (transactional outbox)
- **Tetikleyici:** ödeme `initiated` → `succeeded` geçişi (`PaymentResults.recordSucceeded`). Olay, durum değişikliğiyle
  aynı DB transaction'ında `outbox` tablosuna yazılır. Biri geri alınırsa diğeri de alınır.
- **Gönderim:** `OutboxRelay` yayınlanmamış satırları birkaç saniyede bir (`app.outbox.poll-interval`, varsayılan
  2 sn) broker'a gönderir; broker onayı (publisher confirm) gelince satır yayınlandı işaretlenir.

## Ne zaman yayınlanır

| Durum | Olay |
|---|---|
| Ödeme `initiated` iken başarılı sonuç | `PaymentSucceeded` |
| Ödeme zaten `succeeded` iken başarılı sonuç (tekrar) | Yok |
| Ödeme `failed` iken başarılı sonuç (çelişen sonuç) | Yok; ödeme `failed` kalır, servis WARN yazar |

Sonucu kaydeden çağrı (sağlayıcı webhook'u) sonraki adımda eklenecek; olayın biçimi ondan bağımsızdır.

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` (user-service ve catalog-service ile ortak) |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `payment.succeeded` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder (ör. `payment.succeeded` veya `payment.#`).
Üretici kuyruk tanımlamaz; hiç kuyruk bağlı değilken yayınlanan mesajlar broker tarafından düşürülür. Bu yüzden
consumer kuyruğunu durable tanımlamalı ve ilk yayından önce bind etmiş olmalıdır.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni) = payload'daki `eventId`. Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `PaymentSucceeded` |
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
| `amount` | string | Ödenen tutar, her zaman 2 ondalık (ör. `"149.90"`). Ondalık kaybı olmasın diye metindir; sayıya çevirirken decimal tip (ör. `BigDecimal`) kullanın. |
| `currency` | string | ISO 4217 kodu (ör. `TRY`). |
| `occurredAt` | string (ISO-8601 UTC) | Ödemenin `succeeded` olduğu an (ödeme kaydının `updated_at`'i). Kesir hanesi sayısı değişkendir (0–6). |

Kullanıcı id'si, sağlayıcı ve sağlayıcı referansı bilinçli olarak payload'da **yoktur**. `failureCode` alanı bu olayda
bulunmaz (yalnızca `PaymentFailed`'da).

**`amount` neden metin?** Payload, broker'a gönderilmeden önce outbox tablosunun MySQL `JSON` kolonunda saklanır.
MySQL bu kolonda kesirli sayıları DOUBLE'a çevirir ve sondaki sıfırları atar (`149.90` → `149.9`). Metin olarak
`"149.90"` aynen korunur (catalog `BookUpserted.priceAmount` ile aynı kural).

Örnek (değerler sahte):

```json
{
  "eventVersion": 1,
  "eventId": "01920000-0000-7000-8000-00000000e001",
  "paymentId": "01920000-0000-7000-8000-00000000a001",
  "orderId": "01920000-0000-7000-8000-00000000b001",
  "amount": "149.90",
  "currency": "TRY",
  "occurredAt": "2026-10-04T10:15:30.123456Z"
}
```

## Teslim garantisi ve consumer notları

- **At-least-once.** Aynı olay birden fazla gelebilir (ör. broker onayından sonra, satır yayınlandı işaretlenmeden
  çökme). Consumer **`message_id` / `eventId` ile idempotent** olmalıdır (işlenmiş id'leri saklayıp tekrarı yok saymak).
- Bir ödeme için tek sonuç olayı üretilir (`PaymentSucceeded` ya da `PaymentFailed`); consumer yine de siparişin zaten
  sonuçlanmış olduğunu görürse olayı yok saymalıdır.
- **Sıra garanti edilmez** (birden çok instance, yeniden deneme); farklı ödemelerin olayları birbirine göre her sırada
  gelebilir.

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**: `eventVersion` artırılır.
- Routing key veya exchange adını değiştirmek de kırıcıdır.
