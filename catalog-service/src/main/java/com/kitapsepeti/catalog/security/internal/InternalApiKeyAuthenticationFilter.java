package com.kitapsepeti.catalog.security.internal;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@value #HEADER} başlığındaki anahtarı yapılandırılmış istemcilerle eşleştirir. Eşleşirse principal = istemci
 * adı, yetki {@code ROLE_INTERNAL_SERVICE}; eşleşmezse (başlık yok/yanlış) istek burada 401 ile biter.
 * <p>
 * Bean DEĞİLDİR (yalnızca InternalSecurityConfig'teki zincire eklenir); bean olsaydı Boot onu tüm isteklere
 * uygulanan bir servlet filtresi olarak da kaydederdi.
 */
public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Internal-Api-Key";

	public static final String ROLE = "INTERNAL_SERVICE";

	private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));

	private static final Logger log = LoggerFactory.getLogger(InternalApiKeyAuthenticationFilter.class);

	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
		.getContextHolderStrategy();

	private final InternalApiKeys apiKeys;

	private final AuthenticationEntryPoint entryPoint;

	public InternalApiKeyAuthenticationFilter(InternalApiKeys apiKeys, AuthenticationEntryPoint entryPoint) {
		this.apiKeys = apiKeys;
		this.entryPoint = entryPoint;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER);
		Optional<String> client = (key == null || key.isEmpty()) ? Optional.empty() : this.apiKeys.clientFor(key);
		if (client.isEmpty()) {
			this.securityContextHolderStrategy.clearContext();
			this.entryPoint.commence(request, response,
					new BadCredentialsException("Missing or invalid internal API key"));
			return;
		}
		SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(client.get(), null, AUTHORITIES));
		this.securityContextHolderStrategy.setContext(context);
		log.info("Internal request {} {} client={}", request.getMethod(), request.getRequestURI(), client.get());
		chain.doFilter(request, response);
	}

}
