package com.kitapsepeti.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConvertersTest {

	@Test
	void roleClaimBecomesRoleAuthorityAndSubjectIsThePrincipalName() {
		Jwt jwt = Jwt.withTokenValue("t")
			.header("alg", "RS256")
			.subject("8d6e2f0a-0000-0000-0000-000000000001")
			.claim("role", "ADMIN")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.build();

		AbstractAuthenticationToken authentication = JwtRoleConverters.roleClaim().convert(jwt);

		assertThat(authentication.getName()).isEqualTo("8d6e2f0a-0000-0000-0000-000000000001");
		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.contains("ROLE_ADMIN")
			.noneMatch(authority -> authority.equals("ADMIN") || authority.equals("SCOPE_ADMIN"));
	}

}
