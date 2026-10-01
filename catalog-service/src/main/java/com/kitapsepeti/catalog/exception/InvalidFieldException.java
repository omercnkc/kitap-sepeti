package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.CommonErrorCode;

/**
 * Bean Validation'ın göremediği, servis katmanında anlaşılan alan hatası (ör. olmayan üst kategori).
 * Yanıt, binding hatalarıyla aynı biçimde 400 VALIDATION_FAILED + {@code errors[{field, message}]} olur.
 * {@code message} girilen değeri İÇERMEMELİDİR.
 */
public class InvalidFieldException extends ApiException {

	private final String field;

	private final String fieldMessage;

	public InvalidFieldException(String field, String fieldMessage) {
		super(CommonErrorCode.VALIDATION_FAILED);
		this.field = field;
		this.fieldMessage = fieldMessage;
	}

	public String getField() {
		return this.field;
	}

	public String getFieldMessage() {
		return this.fieldMessage;
	}

}
