package com.kitapsepeti.catalog.exception;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.kitapsepeti.catalog.security.BearerChallenge;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir.
 * İstemciye yalnızca {@link ErrorCode}'daki genel açıklama gider; exception mesajı, Jackson/DB
 * ayrıntısı ve reddedilen alan değerleri (rejectedValue) ne yanıta ne loga yazılır.
 * Security filtrelerinde oluşan 401/403 buraya ulaşmaz; onları security paketindeki handler'lar yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** {@code errors} dizisinin bir elemanı; reddedilen değer bilinçli olarak yok. */
	public record FieldViolation(String field, String message) {
	}

	/** UNAUTHORIZED filtre katmanındaki 401 ile aynı challenge'ı taşır. */
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
		ErrorCode code = ex.getErrorCode();
		ProblemDetails.log(log, code, request, ex);
		ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
		if (code == ErrorCode.UNAUTHORIZED) {
			response.header(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN);
		}
		ProblemDetail problem = ProblemDetails.create(code, ex.getDetail(), request);
		if (ex instanceof InvalidFieldException invalidField) {
			problem.setProperty("errors",
					List.of(new FieldViolation(invalidField.getField(), invalidField.getFieldMessage())));
		}
		if (ex instanceof StockUnavailableException stockUnavailable) {
			problem.setProperty("bookIds", stockUnavailable.getBookIds());
		}
		return response.body(problem);
	}

	/**
	 * DB mesajı kullanıcı verisi (çakışan slug/ISBN) ve SQL içerebilir; kod {@link DbConstraints}'ten gelir,
	 * logda yalnızca constraint adı ve türü yer alır.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		DbConstraints.Violation violation = DbConstraints.classify(ex);
		ErrorCode code = violation.code();
		ProblemDetails.log(log, code, request, ex, violation.logNote());
		return respond(code, code.defaultDetail(), request);
	}

	/** {@code @Version} çakışması; mesaj SQL içerdiği için yazılmaz. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<Object> handleOptimisticLockingFailure(OptimisticLockingFailureException ex,
			HttpServletRequest request) {
		ErrorCode code = ErrorCode.CONCURRENT_MODIFICATION;
		ProblemDetails.log(log, code, request, ex);
		return respond(code, code.defaultDetail(), request);
	}

	/**
	 * Sınıf seviyesinde {@code @Validated} bean'lerin metot doğrulaması (AOP) bu exception'ı fırlatır.
	 * {@code field} = property path'in son parçası (ör. {@code create.request.slug} → {@code slug}).
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		ErrorCode code = ErrorCode.VALIDATION_FAILED;
		ProblemDetails.log(log, code, request, ex);
		ProblemDetail problem = ProblemDetails.create(code, code.defaultDetail(), request);
		problem.setProperty("errors", violations(ex.getConstraintViolations()));
		return ResponseEntity.status(code.status()).body(problem);
	}

	/**
	 * Method security ({@code @PreAuthorize}) reddi controller içinde oluşur. Burada yakalanırsa
	 * catch-all onu 500'e çevirirdi; filtre zincirine geri bırakılır, 401/403 kararını
	 * ExceptionTranslationFilter verir.
	 */
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	public void rethrowSecurityException(RuntimeException ex) {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		ProblemDetails.log(log, code, request, ex);
		return respond(code, code.defaultDetail(), request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setProperty("errors", violations(ex.getBindingResult()));
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/**
	 * ResponseEntityExceptionHandler'ın ele aldığı tüm Spring MVC exception'ları buradan geçer:
	 * status ve header'lar (örn. 405'teki Allow) Spring'den gelir; code, instance ve genel detail eklenir.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ErrorCode code = codeFor(ex, statusCode);
		ProblemDetail problem = problemOf(ex, body, statusCode);
		if (request instanceof ServletWebRequest servletRequest) {
			HttpServletRequest httpRequest = servletRequest.getRequest();
			ProblemDetails.log(log, code, httpRequest, ex);
			ProblemDetails.apply(problem, code, code.defaultDetail(), httpRequest);
		}
		return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
	}

	private static ResponseEntity<Object> respond(ErrorCode code, String detail, HttpServletRequest request) {
		return ResponseEntity.status(code.status()).body(ProblemDetails.create(code, detail, request));
	}

	private static ProblemDetail problemOf(Exception ex, Object body, HttpStatusCode statusCode) {
		if (body instanceof ProblemDetail problem) {
			return problem;
		}
		if (ex instanceof ErrorResponse errorResponse) {
			return errorResponse.getBody();
		}
		return ProblemDetail.forStatus(statusCode);
	}

	private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
		return switch (ex) {
			case MethodArgumentNotValidException e -> ErrorCode.VALIDATION_FAILED;
			case HandlerMethodValidationException e -> ErrorCode.VALIDATION_FAILED;
			case HttpMessageNotReadableException e -> ErrorCode.MALFORMED_REQUEST;
			case NoResourceFoundException e -> ErrorCode.NOT_FOUND;
			case NoHandlerFoundException e -> ErrorCode.NOT_FOUND;
			case HttpRequestMethodNotSupportedException e -> ErrorCode.METHOD_NOT_ALLOWED;
			case HttpMediaTypeNotSupportedException e -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			case HttpMediaTypeNotAcceptableException e -> ErrorCode.NOT_ACCEPTABLE;
			default -> codeForStatus(status);
		};
	}

	private static ErrorCode codeForStatus(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return ErrorCode.INTERNAL_ERROR;
		}
		return switch (status.value()) {
			case 401 -> ErrorCode.UNAUTHORIZED;
			case 403 -> ErrorCode.FORBIDDEN;
			case 404 -> ErrorCode.NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 406 -> ErrorCode.NOT_ACCEPTABLE;
			case 409 -> ErrorCode.CONFLICT;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> ErrorCode.MALFORMED_REQUEST;
		};
	}

	/**
	 * Binding hatalarının (tip dönüşümü) varsayılan mesajı girilen değeri içerir; onun yerine
	 * sabit metin kullanılır. Bean Validation mesajları değer içermez.
	 */
	private static List<FieldViolation> violations(BindingResult result) {
		return result.getAllErrors().stream()
			.map(error -> {
				String field = (error instanceof FieldError fieldError) ? fieldError.getField() : error.getObjectName();
				boolean bindingFailure = error instanceof FieldError fieldError && fieldError.isBindingFailure();
				String message = (bindingFailure || error.getDefaultMessage() == null) ? "invalid value"
						: error.getDefaultMessage();
				return new FieldViolation(field, message);
			})
			.toList();
	}

	/** {@link ConstraintViolation#getInvalidValue()} bilinçli olarak kullanılmaz. Set sırasız; yanıt kararlı olsun diye sıralanır. */
	private static List<FieldViolation> violations(Set<ConstraintViolation<?>> constraintViolations) {
		return constraintViolations.stream()
			.map(violation -> new FieldViolation(lastNodeName(violation.getPropertyPath()), violation.getMessage()))
			.sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
			.toList();
	}

	private static String lastNodeName(Path path) {
		String name = "";
		for (Path.Node node : path) {
			if (node.getName() != null) {
				name = node.getName();
			}
		}
		return name;
	}

}
