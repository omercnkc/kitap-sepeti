package com.kitapsepeti.user.exception;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.ProblemDetail;

/**
 * Controller advice ve security handler'larının ortak ProblemDetail yapısı ve log formatı.
 * Her yanıtta {@code code} ve {@code instance} (istek yolu) bulunur.
 * Log satırı yalnızca method, path ve kod içerir; istek gövdesi, parola, token veya
 * exception mesajı yazılmaz. Stack trace yalnızca ERROR seviyesindeki kodlarda eklenir.
 */
public final class ProblemDetails {

	public static final String CODE_PROPERTY = "code";

	private ProblemDetails() {
	}

	public static ProblemDetail create(ErrorCode code, String detail, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		apply(problem, code, detail, request);
		return problem;
	}

	/** Spring'in ürettiği ProblemDetail'i de aynı yapıya getirir; status'a dokunmaz. */
	public static void apply(ProblemDetail problem, ErrorCode code, String detail, HttpServletRequest request) {
		problem.setDetail(detail);
		problem.setInstance(instanceOf(request));
		problem.setProperty(CODE_PROPERTY, code.name());
	}

	public static void log(Logger log, ErrorCode code, HttpServletRequest request, Throwable ex) {
		log(log, code, request, ex, null);
	}

	/** @param note log satırına eklenecek, kullanıcı verisi İÇERMEYEN ek bilgi (örn. constraint adı) */
	public static void log(Logger log, ErrorCode code, HttpServletRequest request, Throwable ex, String note) {
		LoggingEventBuilder event = log.atLevel(code.logLevel());
		if (code.logLevel() == Level.ERROR && ex != null) {
			event = event.setCause(ex);
		}
		if (note == null) {
			event.log("{} {} -> {}", request.getMethod(), request.getRequestURI(), code.name());
		}
		else {
			event.log("{} {} -> {} ({})", request.getMethod(), request.getRequestURI(), code.name(), note);
		}
	}

	private static URI instanceOf(HttpServletRequest request) {
		try {
			return URI.create(request.getRequestURI());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
