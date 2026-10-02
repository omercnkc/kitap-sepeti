package com.kitapsepeti.cart.exception;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetailExceptionHandler;
import com.kitapsepeti.common.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir; ortak handler'lar tabandan gelir.
 * Burada yalnızca sepete özgü olanlar var: limit aşımında {@code limit} alanı, Catalog kesintisinin log notu ve
 * DB kısıtı → kod eşlemesi ({@link DbConstraintCodes}).
 * Security filtrelerinde oluşan 401/503 buraya ulaşmaz; onları common'daki security handler'ları yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

	/** WARN, stack trace yok; logda yalnızca kök nedenin sınıf adı (mesajı URL/host içerebilir). */
	@ExceptionHandler(CatalogUnavailableException.class)
	public ResponseEntity<Object> handleCatalogUnavailable(CatalogUnavailableException ex, HttpServletRequest request) {
		ErrorCode code = ex.getErrorCode();
		Throwable rootCause = NestedExceptionUtils.getRootCause(ex);
		ProblemDetails.log(this.log, code, request, ex,
				(rootCause != null) ? "cause=" + rootCause.getClass().getSimpleName() : null);
		return respond(code, ex.getDetail(), request);
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
