package com.kitapsepeti.user.config;

import java.util.Arrays;
import java.util.List;

import com.kitapsepeti.user.exception.ErrorCode;
import com.kitapsepeti.user.security.JwtProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 dokümanı ({@code /v3/api-docs}, Swagger UI: {@code /swagger-ui.html}).
 * Varsayılan olarak her operasyon {@code bearerAuth} ister; public uçlar {@code @SecurityRequirements}
 * ile bunu boşaltır. Standart hata yanıtları (401/400/404/500) {@link #standardErrorResponses()} ile
 * her operasyona eklenir; uca özel hatalar controller'larda {@code @ApiResponse} ile yazılır.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	public static final String PROBLEM_JSON = "application/problem+json";

	public static final String PROBLEM_SCHEMA_REF = "#/components/schemas/Problem";

	private static final String PROBLEM = "Problem";

	private static final String FIELD_ERROR = "FieldError";

	@Bean
	public OpenAPI userServiceOpenApi(@Value("${app.version}") String version, JwtProperties jwtProperties) {
		return new OpenAPI()
			.info(new Info()
				.title("Kitap Sepeti — User Service API")
				.version(version)
				.description(description(jwtProperties)))
			// Tanımlanmazsa springdoc isteğin host:port'unu yazar; doküman ortama göre değişir (sözleşme dosyası kayar).
			.servers(List.of(new Server().url("/").description("Dokümanın sunulduğu sunucu")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
			.components(new Components()
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("`POST /api/auth/login` yanıtındaki `accessToken`."))
				.addSchemas(FIELD_ERROR, fieldErrorSchema())
				.addSchemas(PROBLEM, problemSchema()));
	}

	/**
	 * Korumalı operasyonlara 401, gövdesi olanlara 400, path'inde {@code {id}} olanlara 404, hepsine 500.
	 * Operasyonda aynı kod zaten tanımlıysa (uca özel açıklama) dokunulmaz.
	 */
	@Bean
	public OpenApiCustomizer standardErrorResponses() {
		return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
			ApiResponses responses = operation.getResponses();
			if (isProtected(operation)) {
				addIfAbsent(responses, "401", "`UNAUTHORIZED`: token yok, geçersiz, süresi dolmuş ya da kullanıcı silinmiş. "
						+ "Yanıtta `WWW-Authenticate: Bearer` başlığı bulunur.");
			}
			if (operation.getRequestBody() != null) {
				addIfAbsent(responses, "400", "`VALIDATION_FAILED` (alan hataları `errors` dizisinde) "
						+ "veya `MALFORMED_REQUEST` (okunamayan JSON).");
			}
			if (path.contains("{id}")) {
				addIfAbsent(responses, "404", "`RESOURCE_NOT_FOUND`: kayıt yok veya başka bir kullanıcıya ait.");
			}
			addIfAbsent(responses, "500", "`INTERNAL_ERROR`: beklenmeyen hata; ayrıntı yanıtta yer almaz.");
		}));
	}

	/** Operasyon {@code security} tanımlamıyorsa global {@code bearerAuth} geçerlidir; boş liste = public. */
	private static boolean isProtected(Operation operation) {
		return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
	}

	private static void addIfAbsent(ApiResponses responses, String code, String description) {
		if (!responses.containsKey(code)) {
			responses.addApiResponse(code, problemResponse(description));
		}
	}

	private static ApiResponse problemResponse(String description) {
		return new ApiResponse()
			.description(description)
			.content(new Content().addMediaType(PROBLEM_JSON,
					new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
	}

	private static Schema<?> problemSchema() {
		List<String> codes = Arrays.stream(ErrorCode.values()).map(Enum::name).toList();
		return new ObjectSchema()
			.description("RFC 9457 Problem Details. İstemci hatayı `code` alanına göre ayırt etmelidir; "
					+ "`title`/`detail` insan içindir ve değişebilir.")
			.addProperty("type", new StringSchema().format("uri")
				.description("Problem tipi; varsayılan `about:blank` olduğunda yanıtta yer almaz."))
			.addProperty("title", new StringSchema().description("HTTP durumunun kısa adı.").example("Conflict"))
			.addProperty("status", new IntegerSchema().description("HTTP durum kodu.").example(409))
			.addProperty("detail", new StringSchema().description("Genel açıklama; kullanıcı verisi içermez.")
				.example("Email is already registered."))
			.addProperty("instance", new StringSchema().format("uri-reference").description("İsteğin yolu.")
				.example("/api/auth/register"))
			.addProperty("code", new StringSchema()._enum(codes).description("Makine tarafından okunacak hata kodu.")
				.example(ErrorCode.EMAIL_ALREADY_EXISTS.name()))
			.addProperty("errors", new ArraySchema()
				.items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR))
				.description("Yalnızca `VALIDATION_FAILED`'da: alan bazında hatalar. Gönderilen değer yer almaz."))
			.required(List.of("title", "status", "code", "instance"));
	}

	private static Schema<?> fieldErrorSchema() {
		return new ObjectSchema()
			.addProperty("field", new StringSchema().description("Hatalı alanın adı.").example("email"))
			.addProperty("message", new StringSchema().description("Doğrulama mesajı.")
				.example("must be a well-formed email address"))
			.required(List.of("field", "message"));
	}

	private static String description(JwtProperties jwt) {
		return """
				Kullanıcı kaydı, kimlik doğrulama, profil ve adres yönetimi.

				**Kimlik doğrulama:** `POST /api/auth/login` (veya `register`) yanıtındaki `accessToken`'ı her istekte \
				`Authorization: Bearer <accessToken>` başlığıyla gönderin. Access token %d dakika geçerlidir; süresi \
				dolunca `POST /api/auth/refresh` ile yeni bir token çifti alın. Refresh token her kullanımda yenilenir \
				(rotation): yanıttaki yeni refresh token saklanmalı, eskisi artık geçersizdir.

				**Hatalar:** Tüm hatalar RFC 9457 ProblemDetail olarak `application/problem+json` ile döner \
				(`Problem` şeması). İstemci hatayı `code` alanına göre ayırt eder; doğrulama hatalarında `errors` \
				dizisi alan bazında mesaj içerir.
				""".formatted(jwt.accessTtl().toMinutes());
	}

}
