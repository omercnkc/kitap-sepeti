package com.kitapsepeti.order.gateway;

/**
 * İstek gönderildi ama sonucu bilinmiyor: okuma zaman aşımı, bağlantı yanıttan önce koptu, 5xx, ya da 2xx yanıt
 * sözleşmeye uymuyor (okunamayan gövde, eksik zorunlu alan, başka sipariş, beklenmeyen durum). İşlem karşı tarafta
 * uygulanmış OLABİLİR; çağıran idempotent tekrarla ya da sorguyla netleştirmeli (Adım 5 telafisinin temeli).
 */
public record Unknown() implements ReserveResult, CommitResult, ReleaseResult, PaymentInitiationResult {
}
