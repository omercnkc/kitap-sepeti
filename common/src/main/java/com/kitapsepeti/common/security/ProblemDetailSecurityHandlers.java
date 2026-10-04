package com.kitapsepeti.common.security;

import com.kitapsepeti.common.web.RequestPathMasker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401/403 ProblemDetail handler bean'leri. common taranmaz ve auto-configuration değildir; servis bunu
 * kendi SecurityConfig'inde {@code @Import(ProblemDetailSecurityHandlers.class)} ile açıkça alır.
 * Servis bir {@link RequestPathMasker} bean'i tanımlarsa handler'lar onu kullanır; yoksa yalnızca UUID güvenlik ağı.
 */
@Configuration(proxyBeanMethods = false)
public class ProblemDetailSecurityHandlers {

	@Bean
	public ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint(JsonMapper jsonMapper,
			ObjectProvider<RequestPathMasker> pathMasker) {
		return new ProblemDetailAuthenticationEntryPoint(jsonMapper, pathMasker.getIfAvailable(RequestPathMasker::uuidOnly));
	}

	@Bean
	public ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler(JsonMapper jsonMapper,
			ObjectProvider<RequestPathMasker> pathMasker) {
		return new ProblemDetailAccessDeniedHandler(jsonMapper, pathMasker.getIfAvailable(RequestPathMasker::uuidOnly));
	}

}
