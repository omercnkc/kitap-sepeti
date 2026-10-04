package com.kitapsepeti.payment.config;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationEntryPoint;
import com.kitapsepeti.common.security.internal.InternalApiKeyAuthenticationFilter;
import com.kitapsepeti.payment.entity.PaymentStatus;
import com.kitapsepeti.payment.exception.PaymentErrorCode;
import com.kitapsepeti.payment.exception.WebhookSignatureException;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 dokümanı ({@code /v3/api-docs}, Swagger UI: {@code /swagger-ui.html}); cart ile aynı kurulum. Erişim türü
 * yol önekinden çıkar ve {@link #accessRulesAndErrorResponses()} tarafından tek yerde yazılır (güvenlik zincirleriyle
 * aynı kural): {@code /internal/**} → {@code internalApiKey}, {@code /webhooks/**} → {@code mockWebhookSignature};
 * herkese açık operasyon yoktur. Standart hata yanıtları da aynı customizer'da eklenir; uca özel hatalar
 * controller'larda {@code @ApiResponse} ile yazılır.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String INTERNAL_API_KEY = "internalApiKey";

	public static final String MOCK_WEBHOOK_SIGNATURE = "mockWebhookSignature";

	public static final String PROBLEM_JSON = "application/problem+json";

	public static final String PROBLEM_SCHEMA_REF = "#/components/schemas/Problem";

	public static final String PAYMENT_STATUS_SCHEMA_REF = "#/components/schemas/PaymentStatus";

	public static final String TAG_INTERNAL_PAYMENTS = "Internal – Payments";

	public static final String TAG_WEBHOOKS = "Webhooks";

	private static final String PROBLEM = "Problem";

	private static final String FIELD_ERROR = "FieldError";

	private static final String PAYMENT_STATUS = "PaymentStatus";

	@Bean
	public OpenAPI paymentServiceOpenApi(@Value("${app.version}") String version) {
		return new OpenAPI()
			.info(new Info()
				.title("Kitap Sepeti Payment API")
				.version(version)
				.description(DESCRIPTION))
			// Tanımlanmazsa springdoc isteğin host:port'unu yazar; doküman ortama göre değişir (sözleşme dosyası kayar).
			.servers(List.of(new Server().url("/").description("Dokümanın sunulduğu sunucu")))
			.components(new Components()
				.addSecuritySchemes(INTERNAL_API_KEY, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name(InternalApiKeyAuthenticationFilter.HEADER)
					.description("Servisler arası ham anahtar (yalnızca çağıran serviste saklanır; payment yalnızca "
							+ "SHA-256 özetini bilir). v1'de tek istemci order-service."))
				.addSecuritySchemes(MOCK_WEBHOOK_SIGNATURE, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name(MockWebhookSigner.SIGNATURE_HEADER)
					.description("Mock sağlayıcının imzası: `sha256=` + küçük harf hex(HMAC-SHA256(secret, "
							+ "\"<timestamp>.<ham gövde>\")). `timestamp` aynı istekteki `" + MockWebhookSigner.TIMESTAMP_HEADER
							+ "` başlığının değeri (epoch saniye), ham gövde gönderilen baytların kendisi (yeniden "
							+ "serileştirilmiş JSON değil). Zaman damgası sunucu saatinden en fazla ±5 dk sapabilir. "
							+ "Secret yalnızca sağlayıcı ile payment-service arasında paylaşılır."))
				.addSchemas(PAYMENT_STATUS, paymentStatusSchema())
				.addSchemas(FIELD_ERROR, fieldErrorSchema())
				.addSchemas(PROBLEM, problemSchema()));
	}

	/**
	 * Her operasyona erişim türüne göre {@code security} ve standart hataları yazar: girdisi (gövde veya path
	 * değişkeni) olana 400, hepsine 401 ve 500. Operasyonda aynı kod zaten tanımlıysa (uca özel açıklama) dokunulmaz.
	 * Son olarak tüm 4xx/5xx yanıtların gövdesi {@code application/problem+json} yapılır.
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
			openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::dropTypeBesideRef);
		};
	}

	/** {@code @Schema(ref)} verilen String alanda springdoc {@code $ref}'in yanına alanın Java tipini de yazar. */
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
		Access access = Access.of(path);
		operation.setSecurity(access.security());
		ApiResponses responses = operation.getResponses();
		boolean hasBody = operation.getRequestBody() != null;
		boolean hasPathVariable = operation.getParameters() != null
				&& operation.getParameters().stream().anyMatch(parameter -> "path".equals(parameter.getIn()));
		if (hasBody) {
			addIfAbsent(responses, "400", "`VALIDATION_FAILED` (alan hataları `errors` dizisinde) veya "
					+ "`MALFORMED_REQUEST` (okunamayan JSON).");
		}
		else if (hasPathVariable) {
			addIfAbsent(responses, "400", "`MALFORMED_REQUEST`: path değişkeni UUID değil.");
		}
		switch (access) {
			case INTERNAL -> addIfAbsent(responses, "401",
					"`UNAUTHORIZED`: `X-Internal-Api-Key` yok veya tanınmıyor (iki durum aynı yanıtı alır).",
					challenge(InternalApiKeyAuthenticationEntryPoint.CHALLENGE,
							"Her zaman `" + InternalApiKeyAuthenticationEntryPoint.CHALLENGE + "`."));
			case WEBHOOK -> addIfAbsent(responses, "401",
					"`WEBHOOK_SIGNATURE_INVALID`: `" + MockWebhookSigner.SIGNATURE_HEADER + "` ya da `"
							+ MockWebhookSigner.TIMESTAMP_HEADER + "` yok, biçimi bozuk, zaman damgası ±5 dk dışında "
							+ "ya da imza tutmuyor (hepsi aynı yanıtı alır; gövde okunmaz).",
					challenge(WebhookSignatureException.CHALLENGE,
							"Her zaman `" + WebhookSignatureException.CHALLENGE + "`."));
		}
		addIfAbsent(responses, "500", "`INTERNAL_ERROR`: beklenmeyen hata; ayrıntı yanıtta yer almaz.");
		responses.forEach((code, response) -> {
			if (code.charAt(0) == '4' || code.charAt(0) == '5') {
				useProblemContent(response);
			}
		});
	}

	/** InternalSecurityConfig / WebhookSecurityConfig'teki yol kurallarının doküman karşılığı. */
	private enum Access {

		INTERNAL, WEBHOOK;

		static Access of(String path) {
			if (path.startsWith("/internal/")) {
				return INTERNAL;
			}
			if (path.startsWith("/webhooks/")) {
				return WEBHOOK;
			}
			throw new IllegalStateException("Undocumented path prefix: " + path);
		}

		/** Global security tanımlı değil; her operasyon kendi şemasını taşır. */
		List<SecurityRequirement> security() {
			return switch (this) {
				case INTERNAL -> List.of(new SecurityRequirement().addList(INTERNAL_API_KEY));
				case WEBHOOK -> List.of(new SecurityRequirement().addList(MOCK_WEBHOOK_SIGNATURE));
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

	private static void useProblemContent(ApiResponse response) {
		Content content = response.getContent();
		if (content != null && content.size() == 1 && content.containsKey(PROBLEM_JSON)) {
			return;
		}
		response.setContent(new Content().addMediaType(PROBLEM_JSON,
				new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
	}

	private static Schema<?> paymentStatusSchema() {
		return new StringSchema()
			._enum(Arrays.stream(PaymentStatus.values()).map(PaymentStatus::dbValue).toList())
			.description("`initiated`: ödeme oluşturuldu, sonuç bekleniyor (sağlayıcı webhook'u). `succeeded` ve "
					+ "`failed` son durumdur; bir daha değişmez.");
	}

	private static Schema<?> problemSchema() {
		List<String> codes = PaymentErrorCode.API_CODES.stream().map(ErrorCode::name).toList();
		return new ObjectSchema()
			.description("RFC 9457 Problem Details. İstemci hatayı `code` alanına göre ayırt etmelidir; "
					+ "`title`/`detail` insan içindir ve değişebilir.")
			.addProperty("type", new StringSchema().format("uri")
				.description("Problem tipi; varsayılan `about:blank` olduğunda yanıtta yer almaz."))
			.addProperty("title", new StringSchema().description("HTTP durumunun kısa adı.").example("Conflict"))
			.addProperty("status", new IntegerSchema().description("HTTP durum kodu.").example(409))
			.addProperty("detail", new StringSchema().description("Genel açıklama; kullanıcı verisi içermez.")
				.example(PaymentErrorCode.PAYMENT_ORDER_MISMATCH.defaultDetail()))
			.addProperty("instance", new StringSchema().format("uri-reference")
				.description("İsteğin yolu; ödeme id'si `:paymentId` olarak maskelenir.")
				.example("/internal/payments/:paymentId"))
			.addProperty("code", new StringSchema()._enum(codes).description("Makine tarafından okunacak hata kodu.")
				.example(PaymentErrorCode.PAYMENT_ORDER_MISMATCH.name()))
			.addProperty("errors", new ArraySchema()
				.items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR))
				.description("Yalnızca `VALIDATION_FAILED`'da: alan bazında hatalar. Gönderilen değer yer almaz."))
			.required(List.of("title", "status", "code", "instance"));
	}

	private static Schema<?> fieldErrorSchema() {
		return new ObjectSchema()
			.addProperty("field", new StringSchema().description("Hatalı alanın adı.").example("amount"))
			.addProperty("message", new StringSchema().description("Doğrulama mesajı.")
				.example("must be greater than or equal to 0.01"))
			.required(List.of("field", "message"));
	}

	private static final String DESCRIPTION = """
			Sipariş ödemeleri: order-service ödemeyi oluşturur ve sorgular; sağlayıcı sonucu imzalı webhook ile bildirir.

			**Erişim:**
			- Internal (`/internal/**`): yalnızca servisler arası, `X-Internal-Api-Key` başlığı (`internalApiKey`).
			- Webhook (`/webhooks/**`): sağlayıcının HMAC imzası, `X-Mock-Signature` + `X-Mock-Timestamp` başlıkları \
			(`mockWebhookSignature`). v1'de tek sağlayıcı `mock`.

			**Akış:** `POST /internal/payments` ödemeyi `initiated` olarak oluşturur (aynı `orderId` ile tekrar istek \
			aynı ödemeyi döndürür). Sonuç asenkrondur: sağlayıcı webhook'u gelince ödeme `succeeded` ya da `failed` olur \
			ve olay RabbitMQ'ya (`payment.succeeded` / `payment.failed`) yayımlanır; durum \
			`GET /internal/payments/{paymentId}` ile de sorgulanabilir. Mock sağlayıcıda kuruşu 99 olan tutarlar \
			`CARD_DECLINED` ile reddedilir. Kart verisi bu servise hiç gelmez ve saklanmaz.

			**Para:** İstekte ve yanıtta `amount` JSON sayıdır, 2 ondalık basamak; webhook gövdesinde ise JSON metnidir \
			(ör. `"149.90"`).

			**Hatalar:** Tüm hatalar RFC 9457 ProblemDetail olarak `application/problem+json` ile döner \
			(`Problem` şeması). İstemci hatayı `code` alanına göre ayırt eder; doğrulama hatalarında `errors` \
			dizisi alan bazında mesaj içerir.
			""";

}
