package com.kitapsepeti.common.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** user-service'in yayınladığı JWT'lerin claim'lerinden Spring Security kimliği. */
public final class JwtRoleConverters {

	private JwtRoleConverters() {
	}

	/** {@code role} claim'i (USER/ADMIN) → {@code ROLE_USER}/{@code ROLE_ADMIN}; principal adı = {@code sub}. */
	public static JwtAuthenticationConverter roleClaim() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		converter.setPrincipalClaimName("sub");
		return converter;
	}

}
