package com.kitapsepeti.order.config;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.order.exception.OrderErrorCode;
import com.kitapsepeti.order.security.CurrentUserId;
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
 * OpenAPI 3 dokümanı ({@code /v3/api-docs}, Swagger UI: {@code /swagger-ui.html}).
 * Tüm uçlar JWT ile korunur ({@code bearerAuth}); herkese açık operasyon yoktur.
 * Standart hata yanıtları customizer'da eklenir; uca özel durumlar controller'da belgelenir.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	public static final String PROBLEM_JSON = "application/problem+json";

	public static final String PROBLEM_SCHEMA_REF = "#/components/schemas/Problem";

	public static final String TAG_ORDERS = "Orders";

	private static final String PROBLEM = "Problem";

	private static final String FIELD_ERROR = "FieldError";

	static {
		SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
	}

	@Bean
	public OpenAPI orderServiceOpenApi(@Value("${app.version}") String version) {
		return new OpenAPI()
			.info(new Info()
				.title("Kitap Sepeti Order API")
				.version(version)
				.description(DESCRIPTION))
			.servers(List.of(new Server().url("/").description("Dokümanın sunulduğu sunucu")))
			.components(new Components()
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("user-service `POST /api/auth/login` yanıtındaki `accessToken`; rol şartı yok "
							+ "(`USER` ve `ADMIN`). Sipariş token'ın `sub`'ındaki kullanıcınındır."))
				.addSchemas(FIELD_ERROR, fieldErrorSchema())
				.addSchemas(PROBLEM, problemSchema()));
	}

	@Bean
	public OpenApiCustomizer accessRulesAndErrorResponses() {
		return openApi -> {
			if (openApi.getTags() != null) {
				openApi.getTags().sort(Comparator.comparing(Tag::getName));
			}
			openApi.getPaths().forEach((path, item) -> item.readOperations()
				.forEach(operation -> documentOperation(path, operation)));
			if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
				openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::dropTypeBesideRef);
			}
		};
	}

	private static void dropTypeBesideRef(Schema<?> schema) {
		if (schema.getProperties() == null) {
			return;
		}
		schema.getProperties().values().stream().filter(property -> property.get$ref() != null).forEach(property -> {
			property.setType(null);
			property.setTypes(null);
		});
	}

	private static void documentOperation(String path, Operation operation) {
		operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
		ApiResponses responses = operation.getResponses();
		boolean hasBody = operation.getRequestBody() != null;
		boolean hasPathVariable = operation.getParameters() != null
				&& operation.getParameters().stream().anyMatch(parameter -> "path".equals(parameter.getIn()));
		boolean hasQueryParam = operation.getParameters() != null
				&& operation.getParameters().stream().anyMatch(parameter -> "query".equals(parameter.getIn()));

		if (hasBody) {
			addIfAbsent(responses, "400", "`VALIDATION_FAILED` (alan hataları `errors` dizisinde) veya "
					+ "`MALFORMED_REQUEST` (okunamayan JSON" + (hasPathVariable ? " ya da UUID olmayan path değişkeni" : "")
					+ ").");
		}
		else if (hasPathVariable) {
			addIfAbsent(responses, "400", "`MALFORMED_REQUEST`: path değişkeni UUID değil.");
		}
		else if (hasQueryParam) {
			addIfAbsent(responses, "400", "`VALIDATION_FAILED`: geçersiz sayfalama parametreleri.");
		}

		addIfAbsent(responses, "401", "`UNAUTHORIZED`: token yok, geçersiz, süresi dolmuş ya da `sub` "
				+ "kullanıcı id'si (UUID) değil.",
				challenge("Bearer", "Token yoksa `Bearer`, geçersizse `Bearer error=\"invalid_token\"`."));
		addIfAbsent(responses, "503", "`AUTHENTICATION_UNAVAILABLE`: token doğrulanamadı çünkü "
				+ "user-service JWKS ucuna ulaşılamıyor; daha sonra tekrar deneyin.");
		addIfAbsent(responses, "500", "`INTERNAL_ERROR`: beklenmeyen hata; ayrıntı yanıtta yer almaz.");

		responses.forEach((code, response) -> {
			if (code.charAt(0) == '4' || code.charAt(0) == '5') {
				useProblemContent(response);
			}
		});
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

	private static void useProblemContent(ApiResponse response) {
		Content content = response.getContent();
		if (content != null && content.size() == 1 && content.containsKey(PROBLEM_JSON)) {
			return;
		}
		response.setContent(new Content().addMediaType(PROBLEM_JSON,
				new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
	}

	private static Schema<?> problemSchema() {
		List<String> codes = java.util.stream.Stream.concat(
				Arrays.stream(CommonErrorCode.values()).map(ErrorCode::name),
				Arrays.stream(OrderErrorCode.values()).map(ErrorCode::name)
		).distinct().toList();

		return new ObjectSchema()
			.description("RFC 9457 Problem Details. İstemci hatayı `code` alanına göre ayırt etmelidir; "
					+ "`title`/`detail` insan içindir ve değişebilir.")
			.addProperty("type", new StringSchema().format("uri")
				.description("Problem tipi; varsayılan `about:blank` olduğunda yanıtta yer almaz."))
			.addProperty("title", new StringSchema().description("HTTP durumunun kısa adı.").example("Conflict"))
			.addProperty("status", new IntegerSchema().description("HTTP durum kodu.").example(409))
			.addProperty("detail", new StringSchema().description("Genel açıklama; kullanıcı verisi içermez.")
				.example("There is already a pending order; wait for it to complete."))
			.addProperty("instance", new StringSchema().format("uri-reference")
				.description("İsteğin yolu; sipariş id'si `:orderId` olarak maskelenir.")
				.example("/api/orders/checkout"))
			.addProperty("code", new StringSchema()._enum(codes).description("Makine tarafından okunacak hata kodu.")
				.example(OrderErrorCode.ORDER_PENDING_EXISTS.name()))
			.addProperty("orderId", new StringSchema().format("uuid")
				.description("Sipariş id'si. Bekleyen sipariş çakışmasında (`ORDER_PENDING_EXISTS`) veya sipariş yazıldıktan sonraki hatalarda (`INSUFFICIENT_STOCK`, `BOOK_NOT_AVAILABLE`, `CHECKOUT_INTERRUPTED`, `CATALOG_UNAVAILABLE`, `PAYMENT_UNAVAILABLE`, `ORDER_UNAVAILABLE`) bulunur; diğerlerinde yoktur."))
			.addProperty("errors", new ArraySchema()
				.items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR))
				.description("Yalnızca `VALIDATION_FAILED`'da: alan bazında hatalar. Gönderilen değer yer almaz."))
			.required(List.of("title", "status", "code", "instance"));
	}

	private static Schema<?> fieldErrorSchema() {
		return new ObjectSchema()
			.addProperty("field", new StringSchema().description("Hatalı alanın adı.").example("address.city"))
			.addProperty("message", new StringSchema().description("Doğrulama mesajı.")
				.example("must not be blank"))
			.required(List.of("field", "message"));
	}

	private static final String DESCRIPTION = """
			Sipariş yönetimi: kullanıcının sepetinden sipariş oluşturma (checkout), sipariş detayı ve sipariş listesi.

			**Erişim:**
			- Tüm uçlar (`/api/**`): user-service'ten alınan JWT, `Authorization: Bearer <accessToken>` (`bearerAuth`); rol şartı yok (`USER` ve `ADMIN`). Kullanıcı yalnızca token'dan gelir; başka kullanıcının siparişlerine erişilemez.
			- Internal uç yoktur; servisler arası çağrılar bu serviste sunulmaz.

			**Checkout Akışı:** `POST /api/orders/checkout` aktif sepetten sipariş oluşturur. Sipariş önce `pending` durumunda oluşturulur (201 Created + `Location` başlığı). Stok rezervasyonu ve ödeme başlatma adımları sırayla işletilir. Ödeme asenkrondur; ödeme sonucu RabbitMQ üzerinden tüketilir ve sipariş `paid` ya da `failed` olur. İstemci sipariş durumunu `GET /api/orders/{orderId}` ile izler.

			**Sayfalama ve Sıralama:** `GET /api/orders` kullanıcının kendi siparişlerini `created_at DESC, id DESC` sabit sırasıyla sayfalı döner. `page >= 0` (varsayılan 0), `size 1..50` (varsayılan 20). Öğe özeti (`OrderSummaryResponse`) iç durumları ve detayları içermez; `itemCount` farklı kitap (satır) sayısıdır; adet toplamı değildir.

			**Para:** İstekte ve yanıtta para alanları JSON sayıdır, 2 ondalık basamak.

			**Hatalar:** Tüm hatalar RFC 9457 ProblemDetail olarak `application/problem+json` ile döner (`Problem` şeması). İstemci hatayı `code` alanına göre ayırt eder. Sipariş DB'ye yazıldıktan sonraki hatalarda veya bekleyen sipariş çakışmasında yanıtta ek olarak `orderId` alanı bulunur.
			""";

}
