package com.kitapsepeti.catalog.config;

import com.kitapsepeti.common.security.BearerTokenResolvers;
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
 * Stateless, varsayılanı kapalı (deny-by-default) Resource Server ayarı.
 * Oturum/cookie yok; kimlik {@code Authorization: Bearer <JWT>} ile taşınır ve JwtDecoderConfig'teki
 * JwtDecoder (JWKS + RS256 + iss + exp) ile doğrulanır. Katalog okuma uçları herkese açık.
 * 401/403 handler bean'leri common'dan açıkça alınır ({@link ProblemDetailSecurityHandlers}).
 */
@Configuration
@EnableWebSecurity
@Import(ProblemDetailSecurityHandlers.class)
public class SecurityConfig {

	private static final String[] PUBLIC_GET_PATHS = { "/api/books/**", "/api/categories/**" };

	/**
	 * id taşıyan yollar (controller'lardaki {@code {id}}/{@code {orderId}}): hata yanıtının {@code instance}'ında ve
	 * loglarda {@code :<ad>} olur. {@code /api/books/lookup} sabit kalıp olarak kayıtlı; {@code /api/books/{bookId}}'den
	 * önce gelir. Yeni bir uç yolda id taşırsa buraya eklenmeli.
	 */
	@Bean
	public RequestPathMasker requestPathMasker() {
		return RequestPathMasker.of(
				"/api/books/lookup",
				"/api/books/{bookId}",
				"/api/admin/books/isbn-lookup",
				"/api/admin/books/{bookId}",
				"/api/admin/books/{bookId}/publish",
				"/api/admin/books/{bookId}/archive",
				"/api/admin/books/{bookId}/stock-adjustments",
				"/api/admin/books/{bookId}/cover",
				"/api/admin/authors/{authorId}",
				"/api/admin/categories/{categoryId}",
				"/api/admin/categories/{categoryId}/parent",
				"/internal/stock/reservations/{orderId}",
				"/internal/stock/reservations/{orderId}/commit",
				"/internal/stock/reservations/{orderId}/release");
	}

	/** JWKS'e ulaşılamazsa 503; diğer token hataları entry point'e (401). */
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
			// Cookie tabanlı oturum olmadığı için CSRF koruması gereksiz.
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				// Hata yanıtları /error'a yönlendirilir; kapalı olursa her hata 401'e dönüşür.
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				// Servisler arası uçlar InternalSecurityConfig'teki zincire düşer; bu kural yalnızca ek savunma.
				.requestMatchers("/internal/**").denyAll()
				.anyRequest().authenticated())
			// Geçersiz/süresi dolmuş token da aynı ProblemDetail entry point'ine düşer; JWKS'e ulaşılamazsa 503.
			// Herkese açık GET uçlarında Authorization başlığı yok sayılır: süresi dolmuş token'ı göndermeye
			// devam eden istemci kimlik gerektirmeyen katalog okumasında 401 almasın.
			.oauth2ResourceServer(rs -> rs
				.bearerTokenResolver(BearerTokenResolvers.ignoringGet(PUBLIC_GET_PATHS))
				.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverters.roleClaim()))
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
				.withObjectPostProcessor(ProblemDetailAuthenticationFailureHandler.postProcessorFor(
						authenticationFailureHandler)))
			// Token hiç yoksa ExceptionTranslationFilter bu handler'ları kullanır;
			// filtre hataları controller advice'a ulaşmadığı için 401/403 gövdesini bunlar yazar.
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
