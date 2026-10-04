package com.kitapsepeti.order.gateway;

/**
 * Karşı servis isteği 4xx ile reddetti ve bu işlem için ayrıca adlandırılmış bir sonuç yok (ör. 400
 * {@code VALIDATION_FAILED}, 401 {@code UNAUTHORIZED}, Payment 409 {@code PAYMENT_ORDER_MISMATCH}). Sağlayıcılar 4xx'te
 * işlemi uygulamaz (transaction geri alınır); yan etki yok, ama aynı istek tekrarlanınca da aynı yanıt beklenir.
 *
 * @param httpStatus 4xx durum kodu
 * @param code ProblemDetail {@code code} alanı; gövde okunamadıysa null
 */
public record Rejected(int httpStatus, String code) implements ReserveResult, CommitResult, ReleaseResult,
		PaymentInitiationResult {
}
