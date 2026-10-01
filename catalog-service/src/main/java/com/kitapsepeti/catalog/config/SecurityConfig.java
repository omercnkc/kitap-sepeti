package com.kitapsepeti.catalog.config;

import java.util.Arrays;

import com.kitapsepeti.catalog.security.ProblemDetailAccessDeniedHandler;
import com.kitapsepeti.catalog.security.ProblemDetailAuthenticationEntryPoint;
import com.kitapsepeti.catalog.security.ProblemDetailAuthenticationFailureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless, varsayılanı kapalı (deny-by-default) Resource Server ayarı.
 * Oturum/cookie yok; kimlik {@code Authorization: Bearer <JWT>} ile taşınır ve JwtDecoderConfig'teki
 * JwtDecoder (JWKS + RS256 + iss + exp) ile doğrulanır. Katalog okuma uçları herkese açık.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_GET_PATHS = { "/api/books/**", "/api/categories/**" };

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
				// Hata yanıtları /error'a yönlendirilir; kapalı olursa her hata 401'e dönüşür.
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				// Servisler arası uçlar InternalSecurityConfig'teki zincire düşer; bu kural yalnızca ek savunma.
				.requestMatchers("/internal/**").denyAll()
				.anyRequest().authenticated())
			// Geçersiz/süresi dolmuş token da aynı ProblemDetail entry point'ine düşer; JWKS'e ulaşılamazsa 503.
			.oauth2ResourceServer(rs -> rs
				.bearerTokenResolver(bearerTokenResolver())
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
				.withObjectPostProcessor(failureHandlerOf(authenticationFailureHandler)))
			// Token hiç yoksa ExceptionTranslationFilter bu handler'ları kullanır;
			// filtre hataları controller advice'a ulaşmadığı için 401/403 gövdesini bunlar yazar.
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	/** {@code role} claim'i (USER/ADMIN) → {@code ROLE_USER}/{@code ROLE_ADMIN}; principal adı = {@code sub}. */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		converter.setPrincipalClaimName("sub");
		return converter;
	}

	/**
	 * Resource server DSL'inde failure handler ayarı yok; configurer BearerTokenAuthenticationFilter'ı
	 * {@code postProcess}'ten geçirdiği için handler burada bağlanır (entry point ayarından sonra çalışır).
	 */
	private static ObjectPostProcessor<BearerTokenAuthenticationFilter> failureHandlerOf(
			AuthenticationFailureHandler failureHandler) {
		return new ObjectPostProcessor<>() {
			@Override
			public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
				filter.setAuthenticationFailureHandler(failureHandler);
				return filter;
			}
		};
	}

	/**
	 * Herkese açık GET uçlarında Authorization başlığı yok sayılır. Aksi halde süresi dolmuş veya bozuk
	 * token'ı göndermeye devam eden istemci, kimlik gerektirmeyen katalog okumasında 401 alırdı.
	 * Eşleme authorizeHttpRequests ile aynı PathPattern kurallarını kullanır.
	 */
	private static BearerTokenResolver bearerTokenResolver() {
		RequestMatcher publicGet = new OrRequestMatcher(Arrays.stream(PUBLIC_GET_PATHS)
			.map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, path))
			.toList());
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return request -> publicGet.matches(request) ? null : delegate.resolve(request);
	}

}
