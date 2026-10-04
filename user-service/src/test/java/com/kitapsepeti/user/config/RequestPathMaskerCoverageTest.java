package com.kitapsepeti.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Set;
import java.util.TreeSet;

import com.kitapsepeti.common.web.RequestPathMasker;
import com.kitapsepeti.user.ApiTestSupport;
import com.kitapsepeti.user.UserServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Yolunda {@code {değişken}} olan her uç için servisin {@link RequestPathMasker}'ında aynı biçimde bir kalıp olmalı;
 * yoksa yoldaki değer hata yanıtının {@code instance}'ına ve loglara maskesiz (UUID değilse) düşer.
 * Kapsam: yalnızca uygulama sınıfıyla aynı code source'tan (main çıktısı, {@code target/classes}) yüklenen
 * handler'lar. Test probe controller'ları ({@code target/test-classes}) ve framework uçları (jar) bu yüzden dışarıda.
 */
class RequestPathMaskerCoverageTest extends ApiTestSupport {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Autowired
	private RequestPathMasker pathMasker;

	@Test
	void everyTemplatedEndpointHasAMaskerPattern() {
		Set<String> templated = templatedEndpointPatterns();
		assertThat(templated).isNotEmpty();

		assertThat(templated.stream().filter(pattern -> !pathMasker.covers(pattern)).toList())
			.as("RequestPathMasker'da eksik kalıp (SecurityConfig.requestPathMasker); kayıtlı: %s", pathMasker.patterns())
			.isEmpty();
	}

	private Set<String> templatedEndpointPatterns() {
		Set<String> patterns = new TreeSet<>();
		handlerMapping.getHandlerMethods().forEach((info, method) -> {
			if (isMainCode(method.getBeanType())) {
				info.getPatternValues().stream().filter(pattern -> pattern.contains("{")).forEach(patterns::add);
			}
		});
		return patterns;
	}

	private static boolean isMainCode(Class<?> handlerType) {
		return codeSource(ClassUtils.getUserClass(handlerType)).equals(codeSource(UserServiceApplication.class));
	}

	private static URL codeSource(Class<?> type) {
		return type.getProtectionDomain().getCodeSource().getLocation();
	}

}
