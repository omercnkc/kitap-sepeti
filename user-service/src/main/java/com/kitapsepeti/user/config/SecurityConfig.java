package com.kitapsepeti.user.config;

import com.kitapsepeti.common.security.BearerTokenResolvers;
import com.kitapsepeti.common.security.JwtRoleConverters;
import com.kitapsepeti.common.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.common.security.ProblemDetailAuthenticationEntryPoint;
import com.kitapsepeti.common.security.ProblemDetailSecurityHandlers;
import com.kitapsepeti.common.web.RequestPathMasker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless, varsayılanı kapalı (deny-by-default) güvenlik ayarı.
 * Oturum/cookie yok; kimlik {@code Authorization: Bearer <JWT>} ile taşınır ve RsaKeyConfig'teki
 * JwtDecoder (RS256 + iss + exp) ile doğrulanır. Sadece açıkça listelenen yollar herkese açık.
 * Parola kontrolü AuthService içinde {@link PasswordEncoder#matches} ile elle yapıldığı için
 * AuthenticationManager / UserDetailsService tanımlanmaz.
 * {@code @EnableMethodSecurity}: {@code @PreAuthorize} ile uç bazında rol kontrolü.
 * 401/403 handler bean'leri common'dan açıkça alınır ({@link ProblemDetailSecurityHandlers}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(ProblemDetailSecurityHandlers.class)
public class SecurityConfig {

	private static final String AUTH_PATH_PREFIX = "/api/auth/";

	/**
	 * id taşıyan yollar: hata yanıtının {@code instance}'ında ve loglarda {@code :addressId} olur (ortak handler'lar ve
	 * exception handler bu bean'i kullanır). Yeni bir uç yolda id taşırsa buraya eklenmeli.
	 */
	@Bean
	public RequestPathMasker requestPathMasker() {
		return RequestPathMasker.of("/api/me/addresses/{addressId}");
	}

	/** BCrypt, varsayılan strength 10. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
		http
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Cookie tabanlı oturum olmadığı için CSRF koruması gereksiz.
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
				.requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				// Hata yanıtları /error'a yönlendirilir; kapalı olursa her hata 401'e dönüşür.
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			// Geçersiz/süresi dolmuş token da aynı ProblemDetail entry point'ine düşer.
			// Auth uçlarında Authorization başlığı yok sayılır. Aksi halde süresi dolmuş access token'ı başlıkta
			// göndermeye devam eden istemci, tam da token yenilemek istediği /api/auth/refresh'te 401 alırdı.
			.oauth2ResourceServer(rs -> rs
				.bearerTokenResolver(BearerTokenResolvers.ignoringUriPrefix(AUTH_PATH_PREFIX))
				.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverters.roleClaim()))
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			// Token hiç yoksa (veya method security reddi) ExceptionTranslationFilter bu handler'ları kullanır;
			// filtre hataları controller advice'a ulaşmadığı için 401/403 gövdesini bunlar yazar.
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
