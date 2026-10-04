package com.kitapsepeti.payment.config;

import com.kitapsepeti.common.security.ProblemDetailAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * /internal/** ({@link InternalSecurityConfig} sıra 1) ve /webhooks/** ({@link WebhookSecurityConfig} sıra 2) dışındaki
 * her şey. Kullanıcıya açık uç ve JWT yok: yalnızca health, {@code /error} ve OpenAPI dokümanı / Swagger UI açık (Gateway
 * fazında dışarıya kapatılacak), geri kalan her yol reddedilir.
 * <p>
 * Red 403 FORBIDDEN ProblemDetail'dir (401 değil): bu zincirde istemcinin kullanabileceği bir kimlik doğrulama
 * yöntemi yok, 401 ise bir {@code WWW-Authenticate} challenge'ı gerektirir. Bearer challenge bu yüzden hiç yazılmaz;
 * anonim istek de kimlikli istek de aynı 403'ü alır.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** Yalnızca 403 handler'ı; common'daki Bearer challenge yazan entry point bu serviste bean olmamalı. */
	@Bean
	public ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler(JsonMapper jsonMapper) {
		return new ProblemDetailAccessDeniedHandler(jsonMapper);
	}

	@Bean
	@Order(3)
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
		http
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				// Aksi halde hatanın /error'a yönlendirilmesi de reddedilirdi.
				.requestMatchers("/error").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.anyRequest().denyAll())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(denyWithoutChallenge(accessDeniedHandler))
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	/** Anonim isteğin reddi de 403 (challenge'sız 401 yazılamaz). */
	static AuthenticationEntryPoint denyWithoutChallenge(ProblemDetailAccessDeniedHandler accessDeniedHandler) {
		return (request, response, ex) -> accessDeniedHandler.handle(request, response,
				new AccessDeniedException("Access is denied"));
	}

}
