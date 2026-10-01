package com.kitapsepeti.common.security;

import java.io.IOException;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bearer token doğrulama hatalarını ayırır. {@link AuthenticationServiceException} token'ın kendisi değil doğrulama
 * altyapısı başarısız olduğunda gelir (JWKS'e ulaşılamaması, anahtar alınamaması) → 503 AUTHENTICATION_UNAVAILABLE.
 * Diğerleri (imza, süre, issuer, bozuk token) olduğu gibi entry point'e → 401. Spring'in varsayılan işleyicisi
 * AuthenticationServiceException'ı yeniden fırlatır; yanıt container'da 500 olurdu.
 * Log satırında yalnızca kök neden sınıfının kısa adı yer alır (mesaj JWKS URL'si içerir); stack trace yok, Retry-After yok.
 * Yalnızca uzaktan (JWKS) doğrulayan servisler kullanır; bean'i servis kendisi tanımlar ve zincire
 * {@link #postProcessorFor} ile bağlar.
 */
public class ProblemDetailAuthenticationFailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailAuthenticationFailureHandler.class);

	private final AuthenticationEntryPoint authenticationEntryPoint;

	private final JsonMapper jsonMapper;

	public ProblemDetailAuthenticationFailureHandler(AuthenticationEntryPoint authenticationEntryPoint,
			JsonMapper jsonMapper) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {
		if (!(exception instanceof AuthenticationServiceException)) {
			this.authenticationEntryPoint.commence(request, response, exception);
			return;
		}
		ErrorCode code = CommonErrorCode.AUTHENTICATION_UNAVAILABLE;
		String rootCause = NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName();
		ProblemDetails.log(log, code, request, exception, "cause=" + rootCause);
		ProblemDetailResponses.write(this.jsonMapper, response,
				ProblemDetails.create(code, code.defaultDetail(), request));
	}

	/**
	 * Resource server DSL'inde failure handler ayarı yok; configurer BearerTokenAuthenticationFilter'ı
	 * {@code postProcess}'ten geçirdiği için handler burada bağlanır (entry point ayarından sonra çalışır).
	 * Kullanım: {@code oauth2ResourceServer(rs -> rs.withObjectPostProcessor(postProcessorFor(handler)))}.
	 */
	public static ObjectPostProcessor<BearerTokenAuthenticationFilter> postProcessorFor(
			AuthenticationFailureHandler failureHandler) {
		return new ObjectPostProcessor<>() {
			@Override
			public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
				filter.setAuthenticationFailureHandler(failureHandler);
				return filter;
			}
		};
	}

}
