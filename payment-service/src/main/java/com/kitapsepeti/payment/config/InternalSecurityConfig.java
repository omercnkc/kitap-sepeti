package com.kitapsepeti.payment.config;

import java.io.IOException;

import com.kitapsepeti.common.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.common.security.internal.InternalApiKeys;
import com.kitapsepeti.payment.exception.MaskedRequestPaths;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * /internal/** için ayrı, varsayılan zincirden ({@link SecurityConfig}) önce eşleşen zincir (cart ile aynı kurulum).
 * Kimlik yalnızca {@code X-Internal-Api-Key} ile kanıtlanır. İstemciler ve özetler {@link InternalAuthConfig}'te.
 * <p>
 * cart'tan tek fark yol maskeleme: {@code /internal/payments/<id>} yolu ödeme id'si taşır. Ortak filtre başarılı
 * isteği ({@code Internal request ...}) ve entry point reddi ({@code Rejected internal request ...}) istek URI'siyle
 * loglar; filtreye ve handler'lara istek {@link MaskedRequestPaths} üzerinden verilir. Zincirin devamı (yönlendirme
 * dahil) asıl istekle sürer.
 */
@Configuration(proxyBeanMethods = false)
public class InternalSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, InternalApiKeys apiKeys,
			JsonMapper jsonMapper, ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
		InternalApiKeyAuthenticationEntryPoint entryPoint = new InternalApiKeyAuthenticationEntryPoint(jsonMapper);
		AuthenticationEntryPoint maskedEntryPoint = (request, response, ex) -> entryPoint
			.commence(MaskedRequestPaths.mask(request), response, ex);
		AccessDeniedHandler maskedAccessDenied = (request, response, ex) -> accessDeniedHandler
			.handle(MaskedRequestPaths.mask(request), response, ex);
		http
			.securityMatcher("/internal/**")
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.anonymous(AbstractHttpConfigurer::disable)
			.addFilterBefore(new MaskedPathFilter(new InternalApiKeyAuthenticationFilter(apiKeys, maskedEntryPoint)),
					AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth.anyRequest().hasRole(InternalApiKeyAuthenticationFilter.ROLE))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(maskedEntryPoint)
				.accessDeniedHandler(maskedAccessDenied));
		return http.build();
	}

	/**
	 * Sarılan filtreye maskeli isteği verir, zincirin geri kalanına asıl isteği. Bean DEĞİLDİR (yalnızca bu zincirde).
	 */
	static final class MaskedPathFilter implements Filter {

		private final Filter delegate;

		MaskedPathFilter(Filter delegate) {
			this.delegate = delegate;
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
				throws IOException, ServletException {
			this.delegate.doFilter(MaskedRequestPaths.mask((HttpServletRequest) request), response,
					(maskedRequest, sameResponse) -> chain.doFilter(request, sameResponse));
		}

	}

}
