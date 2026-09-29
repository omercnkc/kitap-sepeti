# Olay: `UserRegistered`

Yeni bir kullanıcı kaydolduğunda `user-service` tarafından yayınlanır.

- **Üretici:** `user-service` (transactional outbox → `OutboxRelay`)
- **Tetikleyici:** `POST /api/auth/register` başarılı olduğunda; olay, kullanıcıyla aynı DB transaction'ında
  `outbox` tablosuna yazılır ve arka plandaki worker tarafından broker'a gönderilir.

## Yönlendirme

| | |
|---|---|
| Exchange | `kitapsepeti.events` |
| Exchange tipi | `topic`, durable, auto-delete değil |
| Routing key | `user.registered` |

Consumer, kuyruğunu **kendisi declare edip** bu exchange'e bind eder (ör. `user.registered` veya `user.#`).
Üretici kuyruk tanımlamaz. **Hiç kuyruk bağlı değilken yayınlanan mesajlar broker tarafından düşürülür**
(üretici yine de başarılı sayar); bu yüzden consumer, olayları kaçırmamak için kuyruğunu durable
tanımlamalı ve ilk yayından önce bind etmiş olmalıdır. Consumer exchange'i de (aynı özelliklerle)
declare edebilir; declare işlemi idempotenttir.

## Mesaj özellikleri (AMQP properties)

| Özellik | Değer |
|---|---|
| `message_id` | Outbox satırının id'si (UUID metni). Olay başına sabittir; tekrar gönderimde aynı kalır. |
| `type` | `UserRegistered` |
| `content_type` | `application/json` |
| `content_encoding` | `UTF-8` |
| `timestamp` | Olayın outbox'a yazıldığı an (`created_at`, UTC; AMQP gereği saniye hassasiyetinde) |
| `delivery_mode` | `2` (persistent) |
| header `aggregateType` | `user` |
| header `aggregateId` | Kullanıcı id'si (UUID metni) |

Mesajda ayrıca Spring AMQP'nin publisher confirm eşlemesi için eklediği `spring_returned_message_correlation`
header'ı bulunur (değeri `message_id` ile aynı). Sözleşmenin parçası değildir; consumer yok saymalıdır.

## Payload

UTF-8 JSON nesnesi. Alan sırası ve boşluklar garanti edilmez (JSON olarak ayrıştırın).

| Alan | Tip | Açıklama |
|---|---|---|
| `eventVersion` | integer | Payload şema sürümü; şu an `1`. |
| `userId` | string (UUID) | Kaydolan kullanıcının id'si; `aggregateId` header'ı ile aynı. |
| `email` | string | Kullanıcının e-posta adresi (küçük harfe normalize edilmiş). Kişisel veridir; loglanmamalı. |
| `firstName` | string | Kullanıcının adı. |
| `occurredAt` | string (ISO-8601 UTC, ör. `2026-09-29T13:02:53.4210762Z`) | Kaydın gerçekleştiği an. Kesir hanesi sayısı değişkendir (0–9); standart ISO-8601 ayrıştırıcıyla okuyun. |

Örnek:

```json
{
  "eventVersion": 1,
  "userId": "01a0ed1b-215d-7a27-9658-370bd146cb43",
  "email": "ali@example.com",
  "firstName": "Ali",
  "occurredAt": "2026-09-29T12:19:33.7029414Z"
}
```

Parola, parola hash'i veya token hiçbir zaman payload'da yer almaz.

## Teslim garantisi

**At-least-once.** Worker her mesaj için broker onayını (publisher confirm) bekler ve satırı ancak
onaydan sonra yayınlanmış işaretler. Onay alınıp işaret commit edilmeden önce bir kesinti olursa mesaj
tekrar gönderilir. Bu yüzden consumer **`message_id` ile idempotent** olmalıdır (ör. işlenmiş
`message_id`'leri saklayıp tekrarı yok saymak).

Sıralama: tek üretici instance'ında olaylar `created_at` sırasıyla yayınlanır; bir olay gönderilemezse
sonrakiler de bekler. Birden çok instance veya consumer tarafında yeniden deneme durumunda kesin sıra
garanti edilmez.

## Sürümleme

- Yeni, opsiyonel alan eklemek geriye uyumludur; `eventVersion` değişmez. Consumer bilinmeyen alanları
  yok saymalıdır.
- Alan silmek, yeniden adlandırmak, tipini veya anlamını değiştirmek **kırıcı değişikliktir**:
  `eventVersion` artırılır. Consumer desteklemediği bir sürümü işlememeli (ör. reddedip dead-letter'a göndermeli).
- Routing key veya exchange adını değiştirmek de kırıcıdır.
