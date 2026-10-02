package com.kitapsepeti.cart.config;

import java.util.Comparator;
import java.util.List;

import com.kitapsepeti.cart.exception.CartErrorCode;
import com.kitapsepeti.cart.security.CurrentUserId;
import com.kitapsepeti.common.error.ErrorCode;
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
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 dokümanı ({@code /v3/api-docs}, Swagger UI: {@code /swagger-ui.html}). Erişim türü yol önekinden
 * çıkar ve {@link #accessRulesAndErrorResponses()} tarafından tek yerde yazılır (SecurityConfig /
 * InternalSecurityConfig ile aynı kural): {@code /internal/**} → {@code internalApiKey}, diğer her yol →
 * {@code bearerAuth}; herkese açık operasyon yoktur. Standart hata yanıtları da aynı customizer'da eklenir; uca özel
 * hatalar controller'larda {@code @ApiResponse} ile yazılır.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	public static final String INTERNAL_API_KEY = "internalApiKey";

	public static final String PROBLEM_JSON = "application/problem+json";

	public static final String PROBLEM_SCHEMA_REF = "#/components/schemas/Problem";

	public static final String LIMIT_PROBLEM_SCHEMA_REF = "#/components/schemas/CartLimitProblem";

	public static final String TAG_CART = "Cart";

	public static final String TAG_INTERNAL = "Internal";

	private static final String PROBLEM = "Problem";

	private static final String LIMIT_PROBLEM = "CartLimitProblem";

	private static final String FIELD_ERROR = "FieldError";

	private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

	static {
		// Kullanıcı id'si token'dan çözülür; istemcinin gönderdiği bir parametre değildir.
		SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
	}

	@Bean
	public OpenAPI cartServiceOpenApi(@Value("${app.version}") String version) {
		return new OpenAPI()
			.info(new Info()
				.title("Kitap Sepeti Cart API")
				.version(version)
				.description(DESCRIPTION))
			// Tanımlanmazsa springdoc isteğin host:port'unu yazar; doküman ortama göre değişir (sözleşme dosyası kayar).
			.servers(List.of(new Server().url("/").description("Dokümanın sunulduğu sunucu")))
			.components(new Components()
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("user-service `POST /api/auth/login` yanıtındaki `accessToken`; rol şartı yok "
							+ "(`USER` ve `ADMIN`). Sepet token'ın `sub`'ındaki kullanıcınındır."))
				.addSecuritySchemes(INTERNAL_API_KEY, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name(INTERNAL_API_KEY_HEADER)
					.description("Servisler arası ham anahtar (yalnızca çağıran serviste saklanır; cart yalnızca "
							+ "SHA-256 özetini bilir). Kullanıcı JWT'si bu uçlarda geçersizdir."))
				.addSchemas(FIELD_ERROR, fieldErrorSchema())
				.addSchemas(PROBLEM, problemSchema())
				.addSchemas(LIMIT_PROBLEM, limitProblemSchema()));
	}

	/**
	 * Her operasyona erişim türüne göre {@code security} ve standart hataları yazar: girdisi (gövde veya path
	 * değişkeni) olana 400, kullanıcı uçlarına 401/503, internal'a 401, hepsine 500. Operasyonda aynı kod zaten
	 * tanımlıysa (uca özel açıklama) dokunulmaz. Son olarak tüm 4xx/5xx yanıtların gövdesi
	 * {@code application/problem+json} yapılır; uca özel şema (ör. {@code CartLimitProblem}) korunur.
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
		boolean hasBody = operation.getRequestBody() != null;
		boolean hasPathVariable = operation.getParameters() != null && !operation.getParameters().isEmpty();
		if (hasBody) {
			addIfAbsent(responses, "400", "`VALIDATION_FAILED` (alan hataları `errors` dizisinde) veya "
					+ "`MALFORMED_REQUEST` (okunamayan JSON" + (hasPathVariable ? " ya da UUID olmayan path değişkeni" : "")
					+ ").");
		}
		else if (hasPathVariable) {
			addIfAbsent(responses, "400", "`MALFORMED_REQUEST`: path değişkeni UUID değil.");
		}
		switch (access) {
			case USER -> {
				addIfAbsent(responses, "401", "`UNAUTHORIZED`: token yok, geçersiz, süresi dolmuş ya da `sub` "
						+ "kullanıcı id'si (UUID) değil.",
						challenge("Bearer", "Token yoksa `Bearer`, geçersizse `Bearer error=\"invalid_token\"`."));
				addIfAbsent(responses, "503", "`AUTHENTICATION_UNAVAILABLE`: token doğrulanamadı çünkü "
						+ "user-service JWKS ucuna ulaşılamıyor; daha sonra tekrar deneyin.");
			}
			case INTERNAL -> addIfAbsent(responses, "401",
					"`UNAUTHORIZED`: `X-Internal-Api-Key` yok veya tanınmıyor (iki durum aynı yanıtı alır).",
					challenge("ApiKey realm=\"internal\"", "Her zaman `ApiKey realm=\"internal\"`."));
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

		USER, INTERNAL;

		static Access of(String path) {
			return path.startsWith("/internal/") ? INTERNAL : USER;
		}

		/** Global security tanımlı değil; her operasyon kendi şemasını taşır. */
		List<SecurityRequirement> security() {
			return switch (this) {
				case USER -> List.of(new SecurityRequirement().addList(BEARER_AUTH));
				case INTERNAL -> List.of(new SecurityRequirement().addList(INTERNAL_API_KEY));
			};
		}

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
		List<String> codes = CartErrorCode.API_CODES.stream().map(ErrorCode::name).toList();
		return new ObjectSchema()
			.description("RFC 9457 Problem Details. İstemci hatayı `code` alanına göre ayırt etmelidir; "
					+ "`title`/`detail` insan içindir ve değişebilir.")
			.addProperty("type", new StringSchema().format("uri")
				.description("Problem tipi; varsayılan `about:blank` olduğunda yanıtta yer almaz."))
			.addProperty("title", new StringSchema().description("HTTP durumunun kısa adı.").example("Conflict"))
			.addProperty("status", new IntegerSchema().description("HTTP durum kodu.").example(409))
			.addProperty("detail", new StringSchema().description("Genel açıklama; kullanıcı verisi içermez.")
				.example("Book is not available for sale."))
			.addProperty("instance", new StringSchema().format("uri-reference")
				.description("İsteğin yolu; kitap id'si `:bookId` olarak maskelenir.")
				.example("/api/cart/items/:bookId"))
			.addProperty("code", new StringSchema()._enum(codes).description("Makine tarafından okunacak hata kodu.")
				.example(CartErrorCode.BOOK_NOT_AVAILABLE.name()))
			.addProperty("errors", new ArraySchema()
				.items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR))
				.description("Yalnızca `VALIDATION_FAILED`'da: alan bazında hatalar. Gönderilen değer yer almaz."))
			.required(List.of("title", "status", "code", "instance"));
	}

	/** {@code Problem} + {@code limit}; sepet limitlerinin 409 yanıtında. */
	private static Schema<?> limitProblemSchema() {
		return new Schema<>()
			.description("Sepet çakışması. `CART_LINE_LIMIT_EXCEEDED` ve `CART_QUANTITY_LIMIT_EXCEEDED`'da `limit` "
					+ "aşılan üst sınırı taşır; `BOOK_NOT_AVAILABLE`'da `limit` yoktur.")
			.allOf(List.of(new Schema<>().$ref(PROBLEM_SCHEMA_REF), new ObjectSchema()
				.addProperty("limit", new IntegerSchema()
					.description("Yapılandırılmış üst sınır: farklı kitap sayısı (`CART_LINE_LIMIT_EXCEEDED`) ya da "
							+ "kitap başına adet (`CART_QUANTITY_LIMIT_EXCEEDED`). İstenen değer yanıtta yer almaz.")
					.example(10))));
	}

	private static Schema<?> fieldErrorSchema() {
		return new ObjectSchema()
			.addProperty("field", new StringSchema().description("Hatalı alanın adı.").example("quantity"))
			.addProperty("message", new StringSchema().description("Doğrulama mesajı.")
				.example("must be less than or equal to 99"))
			.required(List.of("field", "message"));
	}

	private static final String DESCRIPTION = """
			Kullanıcı sepeti: oturumdaki kullanıcının tek aktif sepeti ve servisler arası sepet anlık görüntüsü.

			**Erişim:**
			- Sepet (`/api/cart/**`): user-service'ten alınan JWT, `Authorization: Bearer <accessToken>` \
			(`bearerAuth`); rol şartı yok. Kullanıcı yalnızca token'dan gelir; başka kullanıcının sepetine yol yoktur.
			- Internal (`/internal/**`): yalnızca servisler arası, `X-Internal-Api-Key` başlığı (`internalApiKey`). \
			Kullanıcı JWT'si bu uçlarda geçersizdir.

			**Fiyat/stok:** Sepet yanıtları Catalog'daki güncel fiyat ve stokla birleştirilir. Catalog'a \
			ulaşılamazsa okuma ve değişiklik uçları yine 200 döner: `catalogStatus` = `UNAVAILABLE`, satırlarda \
			`available` ve `currentUnitPrice` null, tutarlar sepete eklendiği andaki fiyattan. Yalnızca kitap ekleme \
			Catalog doğrulaması ister (`503 CATALOG_UNAVAILABLE`). Para alanları JSON sayıdır, 2 ondalık basamak.

			**Hatalar:** Tüm hatalar RFC 9457 ProblemDetail olarak `application/problem+json` ile döner \
			(`Problem` şeması). İstemci hatayı `code` alanına göre ayırt eder; doğrulama hatalarında `errors` \
			dizisi alan bazında mesaj içerir. Sepet limiti aşımlarında ek olarak `limit` döner (`CartLimitProblem`).
			""";

}
