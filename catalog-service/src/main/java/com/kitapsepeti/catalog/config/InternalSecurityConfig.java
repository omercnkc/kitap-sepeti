package com.kitapsepeti.catalog.config;

import com.kitapsepeti.catalog.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.catalog.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.catalog.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.catalog.security.internal.InternalApiKeys;
import com.kitapsepeti.catalog.security.internal.InternalAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalSecurityConfig {

	@Bean
	public InternalApiKeys internalApiKeys(InternalAuthProperties properties) {
		return new InternalApiKeys(properties);
	}

	@Bean
	@Order(1)
	public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, InternalApiKeys apiKeys,
			JsonMapper jsonMapper, ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
		InternalApiKeyAuthenticationEntryPoint entryPoint = new InternalApiKeyAuthenticationEntryPoint(jsonMapper);
		http
			.securityMatcher("/internal/**")
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.anonymous(AbstractHttpConfigurer::disable)
			.addFilterBefore(new InternalApiKeyAuthenticationFilter(apiKeys, entryPoint), AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth.anyRequest().hasRole(InternalApiKeyAuthenticationFilter.ROLE))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
