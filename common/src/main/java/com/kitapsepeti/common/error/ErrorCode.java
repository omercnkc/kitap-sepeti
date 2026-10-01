package com.kitapsepeti.common.error;

import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * API'nin döndürdüğü bir hata kodu: HTTP durumu, log seviyesi ve istemciye gösterilecek varsayılan (genel,
 * veri içermeyen) açıklama. Ortak kodlar {@link CommonErrorCode}'da, servise özgü kodlar servisin kendi enum'unda.
 * {@link #name()} ProblemDetail yanıtında {@code code} alanı olarak yazılır; adlar servis içinde benzersiz olmalı.
 * Stack trace yalnızca {@link Level#ERROR} seviyesindeki kodlarda loglanır.
 */
public interface ErrorCode {

	/** Enum sabitinin adı; yanıttaki {@code code} değeri. */
	String name();

	HttpStatus status();

	Level logLevel();

	String defaultDetail();

}
