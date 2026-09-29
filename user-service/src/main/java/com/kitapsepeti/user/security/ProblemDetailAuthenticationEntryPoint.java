package com.kitapsepeti.user.security;

import java.io.IOException;

import com.kitapsepeti.user.exception.ErrorCode;
import com.kitapsepeti.user.exception.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kimliksiz istek korumalı bir yola geldiğinde 401 + ProblemDetail ({@code code=UNAUTHORIZED}) yazar.
 * {@code WWW-Authenticate} header'ı eklenmez; tarayıcı Basic giriş penceresi açmaz.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailAuthenticationEntryPoint.class);

	private final JsonMapper jsonMapper;

	public ProblemDetailAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ErrorCode code = ErrorCode.UNAUTHORIZED;
		ProblemDetails.log(log, code, request, authException);
		ProblemDetailResponses.write(this.jsonMapper, response,
				ProblemDetails.create(code, code.defaultDetail(), request));
	}

}
