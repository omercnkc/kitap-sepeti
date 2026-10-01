package com.kitapsepeti.catalog.security;

import java.io.IOException;

import com.kitapsepeti.catalog.exception.ErrorCode;
import com.kitapsepeti.catalog.exception.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Kimliği doğrulanmış ama yetkisi olmayan isteğe 403 + ProblemDetail ({@code code=FORBIDDEN}) yazar. */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

	private final JsonMapper jsonMapper;

	public ProblemDetailAccessDeniedHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ErrorCode code = ErrorCode.FORBIDDEN;
		ProblemDetails.log(log, code, request, accessDeniedException);
		ProblemDetailResponses.write(this.jsonMapper, response,
				ProblemDetails.create(code, code.defaultDetail(), request));
	}

}
