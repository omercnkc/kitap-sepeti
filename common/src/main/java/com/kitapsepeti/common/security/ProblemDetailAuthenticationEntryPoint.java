package com.kitapsepeti.common.security;

import java.io.IOException;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kimliksiz istek (token yok, bozuk, süresi dolmuş veya başka anahtarla imzalı) korumalı bir yola
 * geldiğinde 401 + ProblemDetail ({@code code=UNAUTHORIZED}) ve {@code WWW-Authenticate: Bearer} yazar.
 * Basic challenge verilmez; tarayıcı giriş penceresi açmaz. Red nedeni ne yanıta ne loga yazılır.
 * Bean kaydı: {@link ProblemDetailSecurityHandlers}.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailAuthenticationEntryPoint.class);

	private final JsonMapper jsonMapper;

	public ProblemDetailAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ErrorCode code = CommonErrorCode.UNAUTHORIZED;
		ProblemDetails.log(log, code, request, authException);
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, (authException instanceof OAuth2AuthenticationException)
				? BearerChallenge.INVALID_TOKEN : BearerChallenge.MISSING_TOKEN);
		ProblemDetailResponses.write(this.jsonMapper, response,
				ProblemDetails.create(code, code.defaultDetail(), request));
	}

}
