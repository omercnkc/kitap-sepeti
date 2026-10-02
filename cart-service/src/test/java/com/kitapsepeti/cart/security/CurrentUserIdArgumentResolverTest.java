package com.kitapsepeti.cart.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import com.kitapsepeti.common.error.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Spring context'siz: decoder'ı atlatan bir kimlik gelse de sonuç 401 (InvalidSubjectException), 500 değil. */
class CurrentUserIdArgumentResolverTest {

	private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void supportsOnlyAnnotatedUuidParameters() throws Exception {
		assertThat(resolver.supportsParameter(parameter("annotatedUuid"))).isTrue();
		assertThat(resolver.supportsParameter(parameter("plainUuid"))).isFalse();
		assertThat(resolver.supportsParameter(parameter("annotatedString"))).isFalse();
	}

	@Test
	void resolvesUuidSubject() throws Exception {
		UUID userId = UUID.randomUUID();
		authenticateWithSubject(userId.toString());

		assertThat(resolver.resolveArgument(parameter("annotatedUuid"), null, null, null)).isEqualTo(userId);
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "1-1-1-1-1", "123E4567-E89B-12D3-A456-426614174000" })
	void nonUuidSubjectIsUnauthorized(String subject) throws Exception {
		authenticateWithSubject(subject);

		assertUnauthorized();
	}

	@Test
	void missingOrNonJwtAuthenticationIsUnauthorized() throws Exception {
		assertUnauthorized();

		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(UUID.randomUUID().toString(), null));
		assertUnauthorized();
	}

	private void assertUnauthorized() throws Exception {
		MethodParameter parameter = parameter("annotatedUuid");
		assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
			.isInstanceOf(InvalidSubjectException.class)
			.satisfies(ex -> assertThat(((InvalidSubjectException) ex).getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
	}

	private static void authenticateWithSubject(String subject) {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.subject(subject)
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	private static MethodParameter parameter(String methodName) throws NoSuchMethodException {
		for (Method method : Signatures.class.getDeclaredMethods()) {
			if (method.getName().equals(methodName)) {
				return new MethodParameter(method, 0);
			}
		}
		throw new NoSuchMethodException(methodName);
	}

	@SuppressWarnings("unused")
	private static final class Signatures {

		void annotatedUuid(@CurrentUserId UUID userId) {
		}

		void plainUuid(UUID userId) {
		}

		void annotatedString(@CurrentUserId String userId) {
		}

	}

}
