package com.kitapsepeti.common.security;

import java.util.Arrays;

import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Herkese açık uçlarda Authorization başlığını yok sayan resolver'lar. Aksi halde süresi dolmuş veya bozuk
 * token'ı göndermeye devam eden istemci, kimlik gerektirmeyen uçta da 401 alırdı. Diğer isteklerde
 * davranış {@link DefaultBearerTokenResolver} ile aynıdır.
 */
public final class BearerTokenResolvers {

	private BearerTokenResolvers() {
	}

	/** {@code ignored} ile eşleşen isteklerde token yok sayılır (null döner). */
	public static BearerTokenResolver ignoring(RequestMatcher ignored) {
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return request -> ignored.matches(request) ? null : delegate.resolve(request);
	}

	/** Verilen yollara gelen GET isteklerinde; eşleme authorizeHttpRequests ile aynı PathPattern kurallarını kullanır. */
	public static BearerTokenResolver ignoringGet(String... pathPatterns) {
		return ignoring(new OrRequestMatcher(Arrays.stream(pathPatterns)
			.map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, path))
			.toList()));
	}

	/** İstek URI'si verilen önekle başlıyorsa (method'dan bağımsız). */
	public static BearerTokenResolver ignoringUriPrefix(String uriPrefix) {
		return ignoring(request -> request.getRequestURI().startsWith(uriPrefix));
	}

}
