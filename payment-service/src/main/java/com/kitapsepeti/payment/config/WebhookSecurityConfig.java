package com.kitapsepeti.payment.config;

import com.kitapsepeti.common.security.ProblemDetailAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sağlayıcı webhook'ları ({@code POST /webhooks/{provider}}). Filtre katmanında kimlik doğrulama yok: istek imzası
 * ham gövdeye bağlı olduğu için controller'da doğrulanır ({@code WebhookController}). Bu yüzden zincir yalnızca
 * {@code POST /webhooks/*}'ı açar; aynı ağaçtaki diğer her metot ve yol 403 (varsayılan zincirle aynı, challenge yok).
 * Internal anahtarı ya da Bearer token burada hiçbir şey ifade etmez.
 */
@Configuration(proxyBeanMethods = false)
public class WebhookSecurityConfig {

	@Bean
	@Order(2)
	public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http,
			ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
		http
			.securityMatcher("/webhooks/**")
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/webhooks/*").permitAll()
				.anyRequest().denyAll())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(SecurityConfig.denyWithoutChallenge(accessDeniedHandler))
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
