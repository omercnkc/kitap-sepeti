package com.kitapsepeti.catalog.config;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.kitapsepeti.catalog.exception.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 dokümanı ({@code /v3/api-docs}, Swagger UI: {@code /swagger-ui.html}). Erişim türü yol önekinden
 * çıkar ve {@link #accessRulesAndErrorResponses()} tarafından tek yerde yazılır (SecurityConfig ile aynı kural):
 * {@code /api/admin/**} → {@code bearerAuth}, {@code /internal/**} → {@code internalApiKey}, diğer {@code /api/**}
 * (yalnızca GET) → herkese açık. Standart hata yanıtları da aynı customizer'da eklenir; uca özel hatalar
 * controller'larda {@code @ApiResponse} ile yazılır.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	public static final String INTERNAL_API_KEY = "internalApiKey";

	public static final String PROBLEM_JSON = "application/problem+json";

	public static final String PROBLEM_SCHEMA_REF = "#/components/schemas/Problem";

	public static final String STOCK_PROBLEM_SCHEMA_REF = "#/components/schemas/StockUnavailableProblem";

	public static final String TAG_BOOKS = "Books";

	public static final String TAG_CATEGORIES = "Categories";

	public static final String TAG_ADMIN_BOOKS = "Admin – Books";

	public static final String TAG_ADMIN_PUBLISHERS = "Admin – Publishers";

	public static final String TAG_ADMIN_AUTHORS = "Admin – Authors";

	public static final String TAG_ADMIN_CATEGORIES = "Admin – Categories";

	public static final String TAG_INTERNAL_STOCK = "Internal – Stock";

	private static final String PROBLEM = "Problem";

	private static final String STOCK_PROBLEM = "StockUnavailableProblem";

	private static final String FIELD_ERROR = "FieldError";

	private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

	@Bean
	public OpenAPI catalogServiceOpenApi(@Value("${app.version}") String version) {
		return new OpenAPI()
			.info(new Info()
				.title("Kitap Sepeti Catalog API")
				.version(version)
				.description(DESCRIPTION))
			// Tanımlanmazsa springdoc isteğin host:port'unu yazar; doküman ortama göre değişir (sözleşme dosyası kayar).
			.servers(List.of(new Server().url("/").description("Dokümanın sunulduğu sunucu")))
			.components(new Components()
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("user-service `POST /api/auth/login` yanıtındaki `accessToken`; `role` = `ADMIN` olmalı."))
				.addSecuritySchemes(INTERNAL_API_KEY, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name(INTERNAL_API_KEY_HEADER)
					.description("Servisler arası ham anahtar (yalnızca çağıran serviste saklanır; catalog yalnızca "
							+ "SHA-256 özetini bilir). Kullanıcı JWT'si bu uçlarda geçersizdir."))
				.addSchemas(FIELD_ERROR, fieldErrorSchema())
				.addSchemas(PROBLEM, problemSchema())
				.addSchemas(STOCK_PROBLEM, stockProblemSchema()));
	}

	/**
	 * Her operasyona erişim türüne göre {@code security} ve standart hataları yazar: girdisi (gövde veya parametre)
	 * olana 400, path değişkeni olana 404, admin'e 401/403/503, internal'a 401, hepsine 500. Operasyonda aynı kod
	 * zaten tanımlıysa (uca özel açıklama) dokunulmaz. Son olarak tüm 4xx/5xx yanıtların gövdesi
	 * {@code application/problem+json} yapılır; uca özel şema (ör. {@code StockUnavailableProblem}) korunur.
	 */
	@Bean
	public OpenApiCustomizer accessRulesAndErrorResponses() {
		return openApi -> {
			// Tag sırası controller tarama sırasına bağlı; sözleşme dosyası kaymasın diye ada göre sıralanır.
			if (openApi.getTags() != null) {
				openApi.getTags().sort(Comparator.comparing(Tag::getName));
			}
			openApi.getPaths().forEach((path, item) -> item.readOperations()
				.forEach(operation -> documentOperation(path, operation)));
		};
	}

	private static void documentOperation(String path, Operation operation) {
		Access access = Access.of(path);
		operation.setSecurity(access.security());
		ApiResponses responses = operation.getResponses();
		if (hasInput(operation)) {
			addIfAbsent(responses, "400", "`VALIDATION_FAILED` (alan hataları `errors` dizisinde) veya "
					+ "`MALFORMED_REQUEST` (okunamayan JSON ya da bozuk path değişkeni).");
		}
		switch (access) {
			case ADMIN -> {
				addIfAbsent(responses, "401", "`UNAUTHORIZED`: token yok, geçersiz ya da süresi dolmuş.",
						challenge("Bearer", "Token yoksa `Bearer`, geçersizse `Bearer error=\"invalid_token\"`."));
				addIfAbsent(responses, "403", "`FORBIDDEN`: token geçerli ama rol `ADMIN` değil.");
				addIfAbsent(responses, "503", "`AUTHENTICATION_UNAVAILABLE`: token doğrulanamadı çünkü "
						+ "user-service JWKS ucuna ulaşılamıyor; daha sonra tekrar deneyin.");
			}
			case INTERNAL -> addIfAbsent(responses, "401",
					"`UNAUTHORIZED`: `X-Internal-Api-Key` yok veya tanınmıyor (iki durum aynı yanıtı alır).",
					challenge("ApiKey realm=\"internal\"", "Her zaman `ApiKey realm=\"internal\"`."));
			case PUBLIC -> {
			}
		}
		if (path.contains("{")) {
			addIfAbsent(responses, "404", "`RESOURCE_NOT_FOUND`: kayıt yok.");
		}
		addIfAbsent(responses, "500", "`INTERNAL_ERROR`: beklenmeyen hata; ayrıntı yanıtta yer almaz.");
		responses.forEach((code, response) -> {
			if (code.charAt(0) == '4' || code.charAt(0) == '5') {
				useProblemContent(response);
			}
		});
	}

	/** SecurityConfig / InternalSecurityConfig'teki yol kurallarının doküman karşılığı. */
	private enum Access {

		PUBLIC, ADMIN, INTERNAL;

		static Access of(String path) {
			if (path.startsWith("/api/admin/")) {
				return ADMIN;
			}
			return path.startsWith("/internal/") ? INTERNAL : PUBLIC;
		}

		/** Boş liste = herkese açık (Swagger UI kilit göstermez); global security tanımlı değil. */
		List<SecurityRequirement> security() {
			return switch (this) {
				case PUBLIC -> List.of();
				case ADMIN -> List.of(new SecurityRequirement().addList(BEARER_AUTH));
				case INTERNAL -> List.of(new SecurityRequirement().addList(INTERNAL_API_KEY));
			};
		}

	}

	private static boolean hasInput(Operation operation) {
		return operation.getRequestBody() != null
				|| (operation.getParameters() != null && !operation.getParameters().isEmpty());
	}

	private static void addIfAbsent(ApiResponses responses, String code, String description) {
		addIfAbsent(responses, code, description, null);
	}

	private static void addIfAbsent(ApiResponses responses, String code, String description, Header challenge) {
		if (!responses.containsKey(code)) {
			ApiResponse response = new ApiResponse().description(description);
			if (challenge != null) {
				response.addHeaderObject("WWW-Authenticate", challenge);
			}
			responses.addApiResponse(code, response);
		}
	}

	private static Header challenge(String example, String description) {
		return new Header().description(description).schema(new StringSchema().example(example));
	}

	/** Uca özel problem şeması verilmişse korunur; aksi halde (içerik yok veya dönüş tipi) {@code Problem}. */
	private static void useProblemContent(ApiResponse response) {
		Content content = response.getContent();
		if (content != null && content.size() == 1 && content.containsKey(PROBLEM_JSON)) {
			return;
		}
		response.setContent(new Content().addMediaType(PROBLEM_JSON,
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
				.example("Slug is already in use."))
			.addProperty("instance", new StringSchema().format("uri-reference").description("İsteğin yolu.")
				.example("/api/admin/publishers"))
			.addProperty("code", new StringSchema()._enum(codes).description("Makine tarafından okunacak hata kodu.")
				.example(ErrorCode.SLUG_ALREADY_EXISTS.name()))
			.addProperty("errors", new ArraySchema()
				.items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR))
				.description("Yalnızca `VALIDATION_FAILED`'da: alan bazında hatalar. Gönderilen değer yer almaz."))
			.required(List.of("title", "status", "code", "instance"));
	}

	/** {@code Problem} + {@code bookIds}; yalnızca stok rezervasyonunun 409 yanıtında. */
	private static Schema<?> stockProblemSchema() {
		return new Schema<>()
			.description("Rezervasyon çakışması. `INSUFFICIENT_STOCK` ve `BOOK_NOT_AVAILABLE`'da `bookIds` hatanın "
					+ "koduyla eşleşen kitapları içerir; `RESERVATION_MISMATCH`'te `bookIds` yoktur.")
			.allOf(List.of(new Schema<>().$ref(PROBLEM_SCHEMA_REF), new ObjectSchema()
				.addProperty("bookIds", new ArraySchema().items(new UUIDSchema())
					.description("Hatanın koduyla eşleşen kitaplar. Hem satışta olmayan hem stoğu yetmeyen kitap "
							+ "varsa kod `BOOK_NOT_AVAILABLE` olur ve yalnızca satışta olmayanlar listelenir."))));
	}

	private static Schema<?> fieldErrorSchema() {
		return new ObjectSchema()
			.addProperty("field", new StringSchema().description("Hatalı alanın adı.").example("slug"))
			.addProperty("message", new StringSchema().description("Doğrulama mesajı.")
				.example("must not be blank"))
			.required(List.of("field", "message"));
	}

	private static final String DESCRIPTION = """
			Kitap kataloğu: herkese açık okuma, admin yönetimi ve servisler arası stok rezervasyonu.

			**Erişim:**
			- Herkese açık (`GET /api/books/**`, `GET /api/categories/**`): kimlik gerekmez; `Authorization` \
			başlığı gönderilse de yok sayılır.
			- Admin (`/api/admin/**`): user-service'ten alınan JWT, `Authorization: Bearer <accessToken>`, \
			`role` = `ADMIN` (`bearerAuth`).
			- Internal (`/internal/**`): yalnızca servisler arası, `X-Internal-Api-Key` başlığı (`internalApiKey`). \
			Kullanıcı JWT'si (ADMIN dahil) bu uçlarda geçersizdir. Ayrıntılar: `docs/api/catalog-internal-stock.md`.

			**Hatalar:** Tüm hatalar RFC 9457 ProblemDetail olarak `application/problem+json` ile döner \
			(`Problem` şeması). İstemci hatayı `code` alanına göre ayırt eder; doğrulama hatalarında `errors` \
			dizisi alan bazında mesaj içerir. Stok rezervasyonu çakışmalarında ek olarak `bookIds` döner \
			(`StockUnavailableProblem`).
			""";

}
