package com.kitapsepeti.common.security;

import java.io.IOException;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.error.ProblemDetails;
import com.kitapsepeti.common.web.RequestPathMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kimliği doğrulanmış ama yetkisi olmayan isteğe 403 + ProblemDetail ({@code code=FORBIDDEN}) yazar; yol
 * {@link RequestPathMasker}'dan geçer. Bean kaydı: {@link ProblemDetailSecurityHandlers}.
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

	private final JsonMapper jsonMapper;

	private final RequestPathMasker pathMasker;

	public ProblemDetailAccessDeniedHandler(JsonMapper jsonMapper) {
		this(jsonMapper, RequestPathMasker.uuidOnly());
	}

	public ProblemDetailAccessDeniedHandler(JsonMapper jsonMapper, RequestPathMasker pathMasker) {
		this.jsonMapper = jsonMapper;
		this.pathMasker = pathMasker;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ErrorCode code = CommonErrorCode.FORBIDDEN;
		ProblemDetails.log(log, code, request, accessDeniedException, null, this.pathMasker);
		ProblemDetailResponses.write(this.jsonMapper, response,
				ProblemDetails.create(code, code.defaultDetail(), request, this.pathMasker));
	}

}
