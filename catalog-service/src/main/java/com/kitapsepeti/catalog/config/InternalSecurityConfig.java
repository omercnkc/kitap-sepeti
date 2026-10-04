package com.kitapsepeti.catalog.config;

import com.kitapsepeti.common.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.common.security.internal.InternalApiKeys;
import com.kitapsepeti.common.web.RequestPathMasker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * /internal/** için ayrı, ana zincirden ({@link SecurityConfig}) önce eşleşen zincir. Kimlik yalnızca
 * {@code X-Internal-Api-Key} ile kanıtlanır; resource server YOK, kullanıcı JWT'si (ADMIN dahil) burada geçersizdir.
 * Filtre bean DEĞİLDİR; yalnızca bu zincire eklenir. İstemciler ve özetler {@link InternalAuthConfig}'te. Filtrenin
 * INFO satırı ve entry point reddi yolu servisin {@link RequestPathMasker}'ından geçirir (sipariş id'si {@code :orderId}).
 */
@Configuration(proxyBeanMethods = false)
public class InternalSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, InternalApiKeys apiKeys,
			JsonMapper jsonMapper, ProblemDetailAccessDeniedHandler accessDeniedHandler, RequestPathMasker pathMasker)
			throws Exception {
		InternalApiKeyAuthenticationEntryPoint entryPoint = new InternalApiKeyAuthenticationEntryPoint(jsonMapper,
				pathMasker);
		http
			.securityMatcher("/internal/**")
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.anonymous(AbstractHttpConfigurer::disable)
			.addFilterBefore(new InternalApiKeyAuthenticationFilter(apiKeys, entryPoint, pathMasker),
					AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth.anyRequest().hasRole(InternalApiKeyAuthenticationFilter.ROLE))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
