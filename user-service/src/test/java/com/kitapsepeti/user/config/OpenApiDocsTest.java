package com.kitapsepeti.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.kitapsepeti.user.TestcontainersConfiguration;
import com.kitapsepeti.user.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String PROBLEM_REF = "#/components/schemas/Problem";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsArePublicOpenApi3Json() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.openapi").value(startsWith("3.")));
	}

	@Test
	void documentsExactlyTheRealOperationsAndNoTestEndpoints() throws Exception {
		Map<String, Map<String, Object>> paths = docs().read("$.paths");
		Set<String> operations = new TreeSet<>();
		paths.forEach((path, item) -> item.keySet().forEach(method -> operations.add(method.toUpperCase() + " " + path)));

		assertThat(operations).containsExactlyInAnyOrder(
				"POST /api/auth/register", "POST /api/auth/login", "POST /api/auth/refresh",
				"GET /.well-known/jwks.json",
				"GET /api/me", "PATCH /api/me",
				"GET /api/me/addresses", "POST /api/me/addresses",
				"GET /api/me/addresses/{id}", "PATCH /api/me/addresses/{id}", "DELETE /api/me/addresses/{id}");
		assertThat(paths.keySet()).noneMatch(path -> path.startsWith("/test/"));
	}

	@Test
	void publicOperationsDropBearerRequirementWhileOthersInheritIt() throws Exception {
		DocumentContext docs = docs();

		assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes.bearerAuth"))
			.containsEntry("type", "http").containsEntry("scheme", "bearer").containsEntry("bearerFormat", "JWT");
		assertThat(docs.<List<Map<String, Object>>>read("$.security")).containsExactly(Map.of("bearerAuth", List.of()));

		assertThat(docs.<List<Object>>read("$.paths['/api/auth/login'].post.security")).isEmpty();
		assertThat(docs.<List<Object>>read("$.paths['/.well-known/jwks.json'].get.security")).isEmpty();
		// Operasyonda security yok → global bearerAuth geçerli.
		assertThat(docs.<Map<String, Object>>read("$.paths['/api/me'].get")).doesNotContainKey("security");
	}

	@Test
	void problemCodeEnumMatchesErrorCodeNames() throws Exception {
		List<String> documented = docs().read("$.components.schemas.Problem.properties.code.enum");

		assertThat(documented).containsExactlyElementsOf(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
	}

	@Test
	void standardAndEndpointSpecificErrorsUseProblemJson() throws Exception {
		DocumentContext docs = docs();

		for (String code : List.of("401", "403", "500")) {
			assertThat(problemRef(docs, "/api/me", "get", code)).as("GET /api/me %s", code).isEqualTo(PROBLEM_REF);
		}
		assertThat(problemRef(docs, "/api/auth/register", "post", "409")).isEqualTo(PROBLEM_REF);
		assertThat(problemRef(docs, "/api/auth/register", "post", "400")).isEqualTo(PROBLEM_REF);
		assertThat(problemRef(docs, "/api/me/addresses/{id}", "patch", "409")).isEqualTo(PROBLEM_REF);
		assertThat(problemRef(docs, "/api/me/addresses/{id}", "delete", "404")).isEqualTo(PROBLEM_REF);
		// Public uçta 401 otomatik eklenmez; login'deki 401 uca özel INVALID_CREDENTIALS açıklamasıdır.
		assertThat(docs.<Map<String, Object>>read("$.paths['/api/auth/register'].post.responses")).doesNotContainKey("401");
		assertThat(docs.<String>read("$.paths['/api/auth/login'].post.responses['401'].description"))
			.contains("INVALID_CREDENTIALS");
		assertThat(docs.<Map<String, Object>>read("$.paths['/api/me/addresses'].post.responses['201'].headers"))
			.containsKey("Location");
	}

	@Test
	void passwordIsWriteOnlyAndHashIsNeverDocumented() throws Exception {
		String json = docsJson();

		assertThat(json).doesNotContain("passwordHash").doesNotContain("password_hash");
		assertThat(JsonPath.<Boolean>read(json, "$.components.schemas.RegisterRequest.properties.password.writeOnly"))
			.isTrue();
	}

	@Test
	void swaggerUiIsPublic() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse();

		if (response.getStatus() == 200) {
			return;
		}
		assertThat(response.getStatus()).isBetween(300, 399);
		assertThat(response.getRedirectedUrl()).endsWith("/swagger-ui/index.html");
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	private static String problemRef(DocumentContext docs, String path, String method, String code) {
		return docs.read("$.paths['%s'].%s.responses['%s'].content['%s'].schema['$ref']"
			.formatted(path, method, code, PROBLEM_JSON));
	}

	private DocumentContext docs() throws Exception {
		return JsonPath.parse(docsJson());
	}

	private String docsJson() throws Exception {
		return mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
