package com.kitapsepeti.common.error;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.kitapsepeti.common.security.BearerChallenge;
import com.kitapsepeti.common.web.RequestPathMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.ClassUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çeviren ortak taban. Servis bunu
 * {@code @RestControllerAdvice} ile işaretli kendi sınıfında genişletir (common'da advice bean'i yoktur);
 * log satırları o alt sınıfın logger adıyla yazılır. Servise özgü exception'lar alt sınıfa
 * {@code @ExceptionHandler} olarak, ApiException ek alanları {@link #addProperties} ile eklenir.
 * İstemciye yalnızca {@link ErrorCode}'daki genel açıklama gider; exception mesajı, Jackson/DB
 * ayrıntısı ve reddedilen alan değerleri (rejectedValue) ne yanıta ne loga yazılır.
 * Security filtrelerinde oluşan 401/403 buraya ulaşmaz; onları security paketindeki handler'lar yazar.
 * {@code instance} ve log satırındaki yol servisin {@link RequestPathMasker} bean'inden geçer (yoksa yalnızca UUID
 * güvenlik ağı); alt sınıflar log/yanıt için {@link #logProblem} ve {@link #respond} kullanır.
 */
public abstract class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

	/** CGLIB alt sınıfı olsa da logger adı servisin advice sınıfı olsun. */
	protected final Logger log = LoggerFactory.getLogger(ClassUtils.getUserClass(getClass()));

	private RequestPathMasker pathMasker = RequestPathMasker.uuidOnly();

	/** Opsiyonel bean: servis kalıp kaydetmezse varsayılan {@link RequestPathMasker#uuidOnly()} kalır. */
	@Autowired(required = false)
	public void setRequestPathMasker(RequestPathMasker pathMasker) {
		this.pathMasker = pathMasker;
	}

	protected final RequestPathMasker pathMasker() {
		return this.pathMasker;
	}

	/** {@code errors} dizisinin bir elemanı; reddedilen değer bilinçli olarak yok. */
	public record FieldViolation(String field, String message) {
	}

	/**
	 * DB kısıt ihlalinin yanıt kodu ve log notu.
	 * @param logNote kısıt adı/türü gibi kullanıcı verisi İÇERMEYEN bilgi; yoksa null
	 */
	public record ConstraintOutcome(ErrorCode code, String logNote) {
	}

	/** UNAUTHORIZED filtre katmanındaki 401 ile aynı challenge'ı taşır. */
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
		ErrorCode code = ex.getErrorCode();
		logProblem(code, request, ex, null);
		ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
		if (code == CommonErrorCode.UNAUTHORIZED) {
			response.header(HttpHeaders.WWW_AUTHENTICATE, BearerChallenge.INVALID_TOKEN);
		}
		ProblemDetail problem = ProblemDetails.create(code, ex.getDetail(), request, this.pathMasker);
		addProperties(problem, ex);
		return response.body(problem);
	}

	/** DB mesajı kullanıcı verisi ve SQL içerebilir; kod ve log notu {@link #classify} ile belirlenir. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		ConstraintOutcome outcome = classify(ex);
		ErrorCode code = outcome.code();
		logProblem(code, request, ex, outcome.logNote());
		return respond(code, code.defaultDetail(), request);
	}

	/**
	 * Sınıf seviyesinde {@code @Validated} bean'lerin metot doğrulaması (AOP) bu exception'ı fırlatır.
	 * {@code field} = property path'in son parçası (ör. {@code create.request.slug} → {@code slug}).
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		ErrorCode code = CommonErrorCode.VALIDATION_FAILED;
		logProblem(code, request, ex, null);
		ProblemDetail problem = ProblemDetails.create(code, code.defaultDetail(), request, this.pathMasker);
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
		ErrorCode code = CommonErrorCode.INTERNAL_ERROR;
		logProblem(code, request, ex, null);
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
			logProblem(code, httpRequest, ex, null);
			ProblemDetails.apply(problem, code, code.defaultDetail(), httpRequest, this.pathMasker);
		}
		return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
	}

	/** ApiException alt türlerinin ek ProblemDetail alanları; varsayılan: yok. */
	protected void addProperties(ProblemDetail problem, ApiException ex) {
	}

	/** Varsayılan: her ihlal CONFLICT; logda Hibernate'in verdiği haliyle kısıt adı. */
	protected ConstraintOutcome classify(DataIntegrityViolationException ex) {
		String constraint = DbConstraints.nameOf(ex);
		return new ConstraintOutcome(CommonErrorCode.CONFLICT, (constraint != null) ? "constraint=" + constraint : null);
	}

	protected ResponseEntity<Object> respond(ErrorCode code, String detail, HttpServletRequest request) {
		return ResponseEntity.status(code.status()).body(ProblemDetails.create(code, detail, request, this.pathMasker));
	}

	/** @param note kullanıcı verisi İÇERMEYEN ek bilgi; yoksa null */
	protected void logProblem(ErrorCode code, HttpServletRequest request, Throwable ex, String note) {
		ProblemDetails.log(this.log, code, request, ex, note, this.pathMasker);
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
			case MethodArgumentNotValidException e -> CommonErrorCode.VALIDATION_FAILED;
			case HandlerMethodValidationException e -> CommonErrorCode.VALIDATION_FAILED;
			case HttpMessageNotReadableException e -> CommonErrorCode.MALFORMED_REQUEST;
			case NoResourceFoundException e -> CommonErrorCode.NOT_FOUND;
			case NoHandlerFoundException e -> CommonErrorCode.NOT_FOUND;
			case HttpRequestMethodNotSupportedException e -> CommonErrorCode.METHOD_NOT_ALLOWED;
			case HttpMediaTypeNotSupportedException e -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
			case HttpMediaTypeNotAcceptableException e -> CommonErrorCode.NOT_ACCEPTABLE;
			default -> codeForStatus(status);
		};
	}

	private static ErrorCode codeForStatus(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return CommonErrorCode.INTERNAL_ERROR;
		}
		return switch (status.value()) {
			case 401 -> CommonErrorCode.UNAUTHORIZED;
			case 403 -> CommonErrorCode.FORBIDDEN;
			case 404 -> CommonErrorCode.NOT_FOUND;
			case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
			case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
			case 409 -> CommonErrorCode.CONFLICT;
			case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> CommonErrorCode.MALFORMED_REQUEST;
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
