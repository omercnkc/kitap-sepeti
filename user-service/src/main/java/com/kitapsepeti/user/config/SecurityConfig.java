package com.kitapsepeti.user.config;

import com.kitapsepeti.user.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.user.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Oturum/cookie yok; kimlik ileride JWT ile taşınacak. Sadece açıkça listelenen
 * yollar herkese açık, geri kalan her istek kimlik doğrulaması ister.
 * Parola kontrolü AuthService içinde {@link PasswordEncoder#matches} ile elle yapılacağı için
 * AuthenticationManager / UserDetailsService tanımlanmaz.
 * {@code @EnableMethodSecurity}: {@code @PreAuthorize} ile uç bazında rol kontrolü.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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
				// Hata yanıtları /error'a yönlendirilir; kapalı olursa her hata 401'e dönüşür.
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			// Basic kapalıyken varsayılan yanıt 403 olur; kimliksiz istek için doğru kod 401.
			// Filtre hataları controller advice'a ulaşmadığı için 401/403 gövdesini bu handler'lar yazar.
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

}
