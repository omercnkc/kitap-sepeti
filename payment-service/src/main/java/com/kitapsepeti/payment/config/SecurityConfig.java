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
 * /internal/** dışındaki her şey ({@link InternalSecurityConfig} sıra 1). Kullanıcıya açık uç ve JWT yok: yalnızca
 * health ve {@code /error} açık, geri kalan her yol reddedilir (webhook Adım 5'te kendi zincirini alacak).
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
	@Order(2)
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
		AuthenticationEntryPoint denyWithoutChallenge = (request, response, ex) -> accessDeniedHandler
			.handle(request, response, new AccessDeniedException("Access is denied"));
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
				.anyRequest().denyAll())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(denyWithoutChallenge)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
