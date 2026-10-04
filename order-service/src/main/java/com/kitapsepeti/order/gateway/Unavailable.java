package com.kitapsepeti.order.gateway;

/**
 * Yan etkisiz okuma (sepet görüntüsü, kitap okuma) yapılamadı: teknik hata, circuit breaker açık, sözleşmeye uymayan
 * yanıt ya da beklenmeyen 4xx. Okuma olduğu için gönderildi/gönderilmedi ayrımı gerekmez.
 */
public record Unavailable() implements CartSnapshotResult, BookLookupResult {
}
