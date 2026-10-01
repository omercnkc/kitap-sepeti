package com.kitapsepeti.common.security.internal;

import java.io.IOException;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetails;
import com.kitapsepeti.common.security.ProblemDetailResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

/**
 * /internal/** için 401 + ProblemDetail ({@code code=UNAUTHORIZED}). Anahtar yok ve yanlış anahtar aynı yanıtı
 * alır. Tek WARN satırı yalnızca method ve path içerir; anahtar veya özeti ASLA yazılmaz.
 */
public class InternalApiKeyAuthenticationEntryPoint implements AuthenticationEntryPoint {

	public static final String CHALLENGE = "ApiKey realm=\"internal\"";

	private static final Logger log = LoggerFactory.getLogger(InternalApiKeyAuthenticationEntryPoint.class);

	private final JsonMapper jsonMapper;

	public InternalApiKeyAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ErrorCode code = CommonErrorCode.UNAUTHORIZED;
		log.warn("Rejected internal request {} {} -> {}", request.getMethod(), request.getRequestURI(), code.name());
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
		ProblemDetailResponses.write(this.jsonMapper, response,
				ProblemDetails.create(code, code.defaultDetail(), request));
	}

}
