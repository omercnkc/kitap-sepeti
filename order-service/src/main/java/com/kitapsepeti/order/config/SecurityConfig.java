package com.kitapsepeti.order.config;

import com.kitapsepeti.common.security.JwtRoleConverters;
import com.kitapsepeti.common.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.common.security.ProblemDetailAuthenticationEntryPoint;
import com.kitapsepeti.common.security.ProblemDetailAuthenticationFailureHandler;
import com.kitapsepeti.common.security.ProblemDetailSecurityHandlers;
import com.kitapsepeti.common.web.RequestPathMasker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Yalnızca Resource Server: token'ı user-service üretir, burada JWKS ile doğrulanır ({@link JwtDecoderConfig}).
 * Herkese açık uçlar yalnızca health ve OpenAPI dokümanı/Swagger UI (cart ile aynı). /api/** kimlik ister (USER ve
 * ADMIN, ayrı rol şartı yok); kimlikli istekte olmayan /api yolu MVC'nin 404'ü. Geri kalan her yol denyAll:
 * kimliksiz 401, kimlikli 403. Order internal uç sunmaz; internal API anahtarı zinciri yok.
 */
@Configuration
@EnableWebSecurity
@Import(ProblemDetailSecurityHandlers.class)
public class SecurityConfig {

	/** id taşıyan yollar; yeni bir uç yolda id taşırsa buraya eklenmeli (ör. {@code /api/orders/{orderId}}). */
	@Bean
	public RequestPathMasker requestPathMasker() {
		return RequestPathMasker.uuidOnly();
	}

	@Bean
	public ProblemDetailAuthenticationFailureHandler problemDetailAuthenticationFailureHandler(
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint, JsonMapper jsonMapper,
			RequestPathMasker pathMasker) {
		return new ProblemDetailAuthenticationFailureHandler(authenticationEntryPoint, jsonMapper, pathMasker);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			ProblemDetailAccessDeniedHandler accessDeniedHandler,
			ProblemDetailAuthenticationFailureHandler authenticationFailureHandler) throws Exception {
		http
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				// Aksi halde controller hatasının /error'a yönlendirilmesi de reddedilirdi.
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverters.roleClaim()))
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
				.withObjectPostProcessor(
						ProblemDetailAuthenticationFailureHandler.postProcessorFor(authenticationFailureHandler)))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
