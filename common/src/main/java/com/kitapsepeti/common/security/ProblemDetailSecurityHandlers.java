package com.kitapsepeti.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401/403 ProblemDetail handler bean'leri. common taranmaz ve auto-configuration değildir; servis bunu
 * kendi SecurityConfig'inde {@code @Import(ProblemDetailSecurityHandlers.class)} ile açıkça alır.
 */
@Configuration(proxyBeanMethods = false)
public class ProblemDetailSecurityHandlers {

	@Bean
	public ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint(JsonMapper jsonMapper) {
		return new ProblemDetailAuthenticationEntryPoint(jsonMapper);
	}

	@Bean
	public ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler(JsonMapper jsonMapper) {
		return new ProblemDetailAccessDeniedHandler(jsonMapper);
	}

}
