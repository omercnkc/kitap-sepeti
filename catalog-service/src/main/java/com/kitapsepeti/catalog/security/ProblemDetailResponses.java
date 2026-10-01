package com.kitapsepeti.catalog.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/** Filtre katmanında (controller advice'a ulaşmadan) ProblemDetail yanıtı yazar. */
public final class ProblemDetailResponses {

	private ProblemDetailResponses() {
	}

	/**
	 * Boot'un {@link JsonMapper}'ı ProblemDetail mixin'ini içerir; böylece {@code code} gibi ek alanlar
	 * MVC yanıtlarındaki gibi kök seviyede yazılır.
	 */
	public static void write(JsonMapper jsonMapper, HttpServletResponse response, ProblemDetail problem) throws IOException {
		if (response.isCommitted()) {
			return;
		}
		response.setStatus(problem.getStatus());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getOutputStream(), problem);
	}

}
