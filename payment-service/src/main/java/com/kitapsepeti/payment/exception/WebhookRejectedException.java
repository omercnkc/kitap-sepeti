package com.kitapsepeti.payment.exception;

import com.kitapsepeti.common.error.ApiException;
import com.kitapsepeti.common.error.ErrorCode;

/**
 * İmza dışındaki webhook retleri (bilinmeyen sağlayıcı, büyük gövde, okunamayan JSON, bilinmeyen ödeme, tutar
 * uyuşmazlığı). Yanıtta yalnızca kodun genel açıklaması var; gövdeden gelen değerler ve neden ayrıntısı yer almaz.
 */
public class WebhookRejectedException extends ApiException {

	public WebhookRejectedException(ErrorCode errorCode) {
		super(errorCode);
	}

}
