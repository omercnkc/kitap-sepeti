package com.kitapsepeti.common.error;

import java.net.URI;

import com.kitapsepeti.common.web.RequestPathMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.ProblemDetail;

/**
 * Controller advice ve security handler'larının ortak ProblemDetail yapısı ve log formatı.
 * Her yanıtta {@code code} ve {@code instance} (istek yolu) bulunur.
 * Log satırı yalnızca method, path ve kod içerir; istek gövdesi, sorgu dizesi, token veya
 * exception mesajı yazılmaz. Stack trace yalnızca ERROR seviyesindeki kodlarda eklenir.
 * Yol hem {@code instance}'ta hem logda {@link RequestPathMasker}'dan geçer; maskeleyici verilmeyen
 * overload'lar {@link RequestPathMasker#uuidOnly()} kullanır.
 */
public final class ProblemDetails {

	public static final String CODE_PROPERTY = "code";

	private ProblemDetails() {
	}

	public static ProblemDetail create(ErrorCode code, String detail, HttpServletRequest request) {
		return create(code, detail, request, RequestPathMasker.uuidOnly());
	}

	public static ProblemDetail create(ErrorCode code, String detail, HttpServletRequest request,
			RequestPathMasker pathMasker) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		apply(problem, code, detail, request, pathMasker);
		return problem;
	}

	/** Spring'in ürettiği ProblemDetail'i de aynı yapıya getirir; status'a dokunmaz. */
	public static void apply(ProblemDetail problem, ErrorCode code, String detail, HttpServletRequest request) {
		apply(problem, code, detail, request, RequestPathMasker.uuidOnly());
	}

	public static void apply(ProblemDetail problem, ErrorCode code, String detail, HttpServletRequest request,
			RequestPathMasker pathMasker) {
		problem.setDetail(detail);
		problem.setInstance(instanceOf(request, pathMasker));
		problem.setProperty(CODE_PROPERTY, code.name());
	}

	public static void log(Logger log, ErrorCode code, HttpServletRequest request, Throwable ex) {
		log(log, code, request, ex, null);
	}

	/** @param note log satırına eklenecek, kullanıcı verisi İÇERMEYEN ek bilgi (örn. constraint adı) */
	public static void log(Logger log, ErrorCode code, HttpServletRequest request, Throwable ex, String note) {
		log(log, code, request, ex, note, RequestPathMasker.uuidOnly());
	}

	/**
	 * @param note log satırına eklenecek, kullanıcı verisi İÇERMEYEN ek bilgi; yoksa null
	 */
	public static void log(Logger log, ErrorCode code, HttpServletRequest request, Throwable ex, String note,
			RequestPathMasker pathMasker) {
		LoggingEventBuilder event = log.atLevel(code.logLevel());
		if (code.logLevel() == Level.ERROR && ex != null) {
			event = event.setCause(ex);
		}
		String path = pathMasker.mask(request);
		if (note == null) {
			event.log("{} {} -> {}", request.getMethod(), path, code.name());
		}
		else {
			event.log("{} {} -> {} ({})", request.getMethod(), path, code.name(), note);
		}
	}

	private static URI instanceOf(HttpServletRequest request, RequestPathMasker pathMasker) {
		String path = pathMasker.mask(request);
		if (path == null) {
			return null;
		}
		try {
			return URI.create(path);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
