package com.kitapsepeti.payment.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Ödemeler yolda ödeme id'siyle adreslenir; ortak handler'lar ise ProblemDetail {@code instance}'ını ve
 * {@code METHOD URI -> CODE} log satırını {@link HttpServletRequest#getRequestURI()}'den üretir. Hata ya da log yazan
 * her noktaya istek buradan geçirilerek verilir: ödeme id'si segmenti {@code :paymentId} olur, diğer yollar aynen
 * kalır (cart-service'teki kalıbın kopyası; Order fazında common'a taşınacak).
 * Süslü parantez kullanılmaz: {@code instance} {@code URI.create} ile üretilir ve geçersiz URI'de boş kalır.
 * Yalnızca yönlendirmeyi etkilemeyen yerlerde kullanılır (hata/security handler'ları, internal anahtar filtresinin
 * logu); dispatch her zaman asıl istekle yapılır.
 */
public final class MaskedRequestPaths {

	static final String PAYMENTS_PATH = "/internal/payments/";

	static final String PAYMENT_ID_SEGMENT = ":paymentId";

	private MaskedRequestPaths() {
	}

	public static HttpServletRequest mask(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String prefix = request.getContextPath() + PAYMENTS_PATH;
		if (uri == null || !uri.startsWith(prefix) || uri.length() == prefix.length()) {
			return request;
		}
		int segmentEnd = uri.indexOf('/', prefix.length());
		String masked = prefix + PAYMENT_ID_SEGMENT + ((segmentEnd < 0) ? "" : uri.substring(segmentEnd));
		return new HttpServletRequestWrapper(request) {
			@Override
			public String getRequestURI() {
				return masked;
			}
		};
	}

	public static WebRequest mask(WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest) {
			HttpServletRequest masked = mask(servletRequest.getRequest());
			if (masked != servletRequest.getRequest()) {
				return new ServletWebRequest(masked, servletRequest.getResponse());
			}
		}
		return request;
	}

}
