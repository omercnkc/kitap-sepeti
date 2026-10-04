package com.kitapsepeti.order.gateway;

/**
 * İstek karşı servise ulaşmadı: bağlantı kurulamadı (reddedildi, bağlantı zaman aşımı, adres çözülemedi) ya da
 * circuit breaker açık olduğu için hiç gönderilmedi. Yan etki YOK; telafi gerekmez, aynı istek güvenle tekrarlanabilir.
 */
public record NotPerformed() implements ReserveResult, CommitResult, ReleaseResult, PaymentInitiationResult {
}
