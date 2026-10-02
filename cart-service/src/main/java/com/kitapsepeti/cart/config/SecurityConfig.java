package com.kitapsepeti.cart.config;

import com.kitapsepeti.cart.exception.MaskedRequestPaths;
import com.kitapsepeti.common.security.JwtRoleConverters;
import com.kitapsepeti.common.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.common.security.ProblemDetailAuthenticationEntryPoint;
import com.kitapsepeti.common.security.ProblemDetailAuthenticationFailureHandler;
import com.kitapsepeti.common.security.ProblemDetailSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Yalnızca Resource Server: token'ı user-service üretir, burada JWKS ile doğrulanır ({@link JwtDecoderConfig}).
 * Herkese açık uç yalnızca health; sepetin tamamı kimlik ister (USER ve ADMIN, ayrı rol şartı yok).
 * Diğer yollar da kimlik ister (catalog ile aynı): kimliksiz 401, kimlikli olmayan yol 404.
 * Ortak security handler'larına istek {@link MaskedRequestPaths} üzerinden verilir (yoldaki kitap id'si logda/yanıtta yok).
 */
@Configuration
@EnableWebSecurity
@Import(ProblemDetailSecurityHandlers.class)
public class SecurityConfig {

	@Bean
	public ProblemDetailAuthenticationFailureHandler problemDetailAuthenticationFailureHandler(
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint, JsonMapper jsonMapper) {
		return new ProblemDetailAuthenticationFailureHandler(authenticationEntryPoint, jsonMapper);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			ProblemDetailAccessDeniedHandler accessDeniedHandler,
			ProblemDetailAuthenticationFailureHandler authenticationFailureHandler) throws Exception {
		AuthenticationEntryPoint maskedEntryPoint = (request, response, ex) -> authenticationEntryPoint
			.commence(MaskedRequestPaths.mask(request), response, ex);
		AccessDeniedHandler maskedAccessDenied = (request, response, ex) -> accessDeniedHandler
			.handle(MaskedRequestPaths.mask(request), response, ex);
		AuthenticationFailureHandler maskedFailure = (request, response, ex) -> authenticationFailureHandler
			.onAuthenticationFailure(MaskedRequestPaths.mask(request), response, ex);
		http
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				// Aksi halde controller hatasının /error'a yönlendirilmesi de 401 olurdu.
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/cart/**").authenticated()
				.anyRequest().authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverters.roleClaim()))
				.authenticationEntryPoint(maskedEntryPoint)
				.accessDeniedHandler(maskedAccessDenied)
				.withObjectPostProcessor(ProblemDetailAuthenticationFailureHandler.postProcessorFor(maskedFailure)))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(maskedEntryPoint)
				.accessDeniedHandler(maskedAccessDenied));
		return http.build();
	}

}
