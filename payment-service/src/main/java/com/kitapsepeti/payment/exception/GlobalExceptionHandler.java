package com.kitapsepeti.payment.exception;

import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetailExceptionHandler;
import com.kitapsepeti.common.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir; ortak handler'lar tabandan gelir.
 * Burada yalnızca ödemeye özgü olanlar var: sağlayıcı kesintisinin log notu, webhook imza reddi ve DB kısıtı → kod
 * eşlemesi ({@link DbConstraintCodes}). Yoldaki ödeme id'si tabanda maskelenir (servisin {@code RequestPathMasker}
 * bean'i, {@code SecurityConfig}).
 * Security filtrelerinde oluşan 401/403 buraya ulaşmaz; onları security handler'ları yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

	/** WARN, stack trace yok; logda yalnızca kök nedenin sınıf adı (mesajı tutar/id/host içerebilir). */
	@ExceptionHandler(PaymentProviderUnavailableException.class)
	public ResponseEntity<Object> handleProviderUnavailable(PaymentProviderUnavailableException ex,
			HttpServletRequest request) {
		ErrorCode code = ex.getErrorCode();
		Throwable rootCause = NestedExceptionUtils.getRootCause(ex);
		logProblem(code, request, ex, (rootCause != null) ? "cause=" + rootCause.getClass().getSimpleName() : null);
		return respond(code, ex.getDetail(), request);
	}

	/**
	 * Tek WARN satırı, yalnızca sabit metin ve sağlayıcı adı: yol, imza, zaman damgası ve hangi kontrolün başarısız
	 * olduğu yazılmaz.
	 */
	@ExceptionHandler(WebhookSignatureException.class)
	public ResponseEntity<Object> handleWebhookSignature(WebhookSignatureException ex, HttpServletRequest request) {
		this.log.warn("Rejected webhook: invalid signature (provider={})", ex.getProvider().dbValue());
		ErrorCode code = ex.getErrorCode();
		return ResponseEntity.status(code.status())
			.header(HttpHeaders.WWW_AUTHENTICATE, WebhookSignatureException.CHALLENGE)
			.body(ProblemDetails.create(code, ex.getDetail(), request, pathMasker()));
	}

	/** DB mesajı SQL ve sipariş/kullanıcı id'si içerebilir; logda yalnızca kısıt adı ve türü yer alır. */
	@Override
	protected ConstraintOutcome classify(DataIntegrityViolationException ex) {
		DbConstraintCodes.Violation violation = DbConstraintCodes.classify(ex);
		return new ConstraintOutcome(violation.code(), violation.logNote());
	}

}
