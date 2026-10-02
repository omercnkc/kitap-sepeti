package com.kitapsepeti.cart.exception;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetailExceptionHandler;
import com.kitapsepeti.common.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir; ortak handler'lar tabandan gelir.
 * Burada yalnızca sepete özgü olanlar var: limit aşımında {@code limit} alanı, Catalog kesintisinin log notu,
 * DB kısıtı → kod eşlemesi ({@link DbConstraintCodes}) ve yoldaki kitap id'sinin maskelenmesi
 * ({@link MaskedRequestPaths}; isteği alan her taban handler'ı bu yüzden yeniden tanımlanır).
 * Security filtrelerinde oluşan 401/503 buraya ulaşmaz; onları common'daki security handler'ları yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

	/** WARN, stack trace yok; logda yalnızca kök nedenin sınıf adı (mesajı URL/host içerebilir). */
	@ExceptionHandler(CatalogUnavailableException.class)
	public ResponseEntity<Object> handleCatalogUnavailable(CatalogUnavailableException ex, HttpServletRequest request) {
		HttpServletRequest masked = MaskedRequestPaths.mask(request);
		ErrorCode code = ex.getErrorCode();
		Throwable rootCause = NestedExceptionUtils.getRootCause(ex);
		ProblemDetails.log(this.log, code, masked, ex,
				(rootCause != null) ? "cause=" + rootCause.getClass().getSimpleName() : null);
		return respond(code, ex.getDetail(), masked);
	}

	@Override
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
		return super.handleApiException(ex, MaskedRequestPaths.mask(request));
	}

	@Override
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		return super.handleDataIntegrityViolation(ex, MaskedRequestPaths.mask(request));
	}

	@Override
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		return super.handleConstraintViolation(ex, MaskedRequestPaths.mask(request));
	}

	@Override
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
		return super.handleUnexpected(ex, MaskedRequestPaths.mask(request));
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		return super.handleExceptionInternal(ex, body, headers, statusCode, MaskedRequestPaths.mask(request));
	}

	@Override
	protected void addProperties(ProblemDetail problem, ApiException ex) {
		if (ex instanceof CartLimitExceededException limitExceeded) {
			problem.setProperty("limit", limitExceeded.getLimit());
		}
	}

	/** DB mesajı SQL ve kullanıcı/kitap id'si içerebilir; logda yalnızca kısıt adı ve türü yer alır. */
	@Override
	protected ConstraintOutcome classify(DataIntegrityViolationException ex) {
		DbConstraintCodes.Violation violation = DbConstraintCodes.classify(ex);
		return new ConstraintOutcome(violation.code(), violation.logNote());
	}

}
