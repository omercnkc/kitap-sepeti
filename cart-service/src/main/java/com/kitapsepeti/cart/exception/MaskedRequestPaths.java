package com.kitapsepeti.cart.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Sepet satırları yolda kitap id'siyle adreslenir; ortak handler'lar ise ProblemDetail {@code instance}'ını ve
 * {@code METHOD URI -> CODE} log satırını {@link HttpServletRequest#getRequestURI()}'den üretir. Hata yazan her
 * noktaya istek buradan geçirilerek verilir: kitap id'si segmenti {@code :bookId} olur, diğer yollar aynen kalır.
 * Süslü parantez kullanılmaz: {@code instance} {@code URI.create} ile üretilir ve geçersiz URI'de boş kalır.
 * Yalnızca yönlendirme bittikten sonra (hata handler'ları, security handler'ları) kullanılır; dispatch'i etkilemez.
 */
public final class MaskedRequestPaths {

	static final String ITEMS_PATH = "/api/cart/items/";

	static final String BOOK_ID_SEGMENT = ":bookId";

	private MaskedRequestPaths() {
	}

	public static HttpServletRequest mask(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String prefix = request.getContextPath() + ITEMS_PATH;
		if (uri == null || !uri.startsWith(prefix) || uri.length() == prefix.length()) {
			return request;
		}
		int segmentEnd = uri.indexOf('/', prefix.length());
		String masked = prefix + BOOK_ID_SEGMENT + ((segmentEnd < 0) ? "" : uri.substring(segmentEnd));
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
