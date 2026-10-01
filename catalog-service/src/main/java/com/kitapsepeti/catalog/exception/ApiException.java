package com.kitapsepeti.catalog.exception;

/**
 * Uygulamanın bilinçli olarak fırlattığı iş kuralı hatalarının tabanı.
 * {@link GlobalExceptionHandler} bunu {@link ErrorCode}'daki durum ve kodla ProblemDetail'e çevirir.
 * Özel detail istemciye gider; kullanıcı verisi veya gizli bilgi İÇERMEMELİDİR.
 */
public abstract class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	protected ApiException(ErrorCode errorCode) {
		this(errorCode, errorCode.defaultDetail());
	}

	protected ApiException(ErrorCode errorCode, String detail) {
		super(detail);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return this.errorCode;
	}

	public String getDetail() {
		return getMessage();
	}

}
