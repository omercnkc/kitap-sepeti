package com.kitapsepeti.catalog.exception;

import java.util.List;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetailExceptionHandler;
import com.kitapsepeti.common.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir; ortak handler'lar tabandan gelir.
 * Burada yalnızca katalog'a özgü olanlar var: DB kısıtı → kod eşlemesi ({@link DbConstraintCodes}),
 * {@code @Version} çakışması ve bazı ApiException'ların ek alanları ({@code errors}, {@code bookIds}).
 * Security filtrelerinde oluşan 401/403/503 buraya ulaşmaz; onları common'daki security handler'ları yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

	/** {@code @Version} çakışması; mesaj SQL içerdiği için yazılmaz. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<Object> handleOptimisticLockingFailure(OptimisticLockingFailureException ex,
			HttpServletRequest request) {
		ErrorCode code = CatalogErrorCode.CONCURRENT_MODIFICATION;
		ProblemDetails.log(this.log, code, request, ex);
		return respond(code, code.defaultDetail(), request);
	}

	@Override
	protected void addProperties(ProblemDetail problem, ApiException ex) {
		if (ex instanceof InvalidFieldException invalidField) {
			problem.setProperty("errors",
					List.of(new FieldViolation(invalidField.getField(), invalidField.getFieldMessage())));
		}
		if (ex instanceof StockUnavailableException stockUnavailable) {
			problem.setProperty("bookIds", stockUnavailable.getBookIds());
		}
	}

	/**
	 * DB mesajı kullanıcı verisi (çakışan slug/ISBN) ve SQL içerebilir; kod {@link DbConstraintCodes}'tan gelir,
	 * logda yalnızca constraint adı ve türü yer alır.
	 */
	@Override
	protected ConstraintOutcome classify(DataIntegrityViolationException ex) {
		DbConstraintCodes.Violation violation = DbConstraintCodes.classify(ex);
		return new ConstraintOutcome(violation.code(), violation.logNote());
	}

}
