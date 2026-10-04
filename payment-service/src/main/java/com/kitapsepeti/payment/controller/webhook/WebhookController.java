package com.kitapsepeti.payment.controller.webhook;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.payment.config.OpenApiConfig;
import com.kitapsepeti.payment.config.PaymentProperties;
import com.kitapsepeti.payment.dto.webhook.WebhookEvent;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.exception.PaymentErrorCode;
import com.kitapsepeti.payment.exception.WebhookRejectedException;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import com.kitapsepeti.payment.provider.mock.MockWebhookVerifier;
import com.kitapsepeti.payment.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Sağlayıcı webhook'u: {@code POST /webhooks/{provider}} (güvenlik zinciri {@code WebhookSecurityConfig}; kimlik
 * imzadır). Kontrol sırası: sağlayıcı (etkin sağlayıcı değilse 404, gövde okunmaz) → Content-Type
 * ({@code application/json} değilse 415) → gövde boyutu (413) → imza (401) → JSON → doğrulama (400) →
 * {@link WebhookService}. İmza ham baytlar üzerinde, JSON'dan ÖNCE doğrulanır: imzasız gövde hiç parse edilmez.
 * İşlenen ve tekrar olay 204 alır (gövde yok). Loglarda gövde, imza, zaman damgası, olay/ödeme kimliği ve tutar yok.
 */
@RestController
@Tag(name = OpenApiConfig.TAG_WEBHOOKS, description = "Ödeme sağlayıcısından gelen imzalı sonuç bildirimleri.")
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

	private final MockWebhookVerifier verifier;

	private final WebhookService webhookService;

	private final Validator validator;

	private final PaymentProviderType activeProvider;

	private final int maxBodyBytes;

	/** Boot'un mapper'ı + katı okuma: metin alanına sayı/boolean gelmez, sonda fazladan içerik ve tekrar anahtar yok. */
	private final JsonMapper reader;

	public WebhookController(MockWebhookVerifier verifier, WebhookService webhookService, Validator validator,
			PaymentProperties properties, JsonMapper jsonMapper) {
		this.verifier = verifier;
		this.webhookService = webhookService;
		this.validator = validator;
		this.activeProvider = properties.provider();
		this.maxBodyBytes = properties.webhook().maxBodyBytes();
		this.reader = jsonMapper.rebuild()
			.withCoercionConfig(LogicalType.Textual, config -> config
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.build();
	}

	@PostMapping("/webhooks/{provider}")
	@Operation(operationId = "receiveWebhook", summary = "Sağlayıcı ödeme sonucu webhook'u",
			description = "Sağlayıcı ödemenin sonucunu bildirir; ödeme `succeeded` ya da `failed` olur ve olay "
					+ "RabbitMQ'ya yayımlanır. Kontrol sırası: sağlayıcı (404) → Content-Type (415) → gövde boyutu "
					+ "(413) → imza (401) → JSON ve alanlar (400) → ödeme ve tutar (400). İmza ham "
					+ "gövde üzerinden doğrulanır; imzasız gövde hiç okunmaz. Aynı `eventId` ile tekrar gönderim ve "
					+ "sonuçlanmış ödeme için gelen olay da 204 alır (ödeme değişmez). Bilinmeyen alanlar yok sayılır.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = WebhookEvent.class))))
	@Parameter(name = MockWebhookSigner.TIMESTAMP_HEADER, in = ParameterIn.HEADER, required = true,
			description = "İmzalanan zaman damgası (epoch saniye). Sunucu saatinden en fazla ±5 dk sapabilir; yoksa "
					+ "ya da biçimi bozuksa 401.",
			schema = @Schema(type = "string", pattern = "^[0-9]{1,18}$"))
	@ApiResponse(responseCode = "204", description = "Olay işlendi ya da daha önce işlenmişti (gövde yok).")
	@ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` (alan hataları `errors` dizisinde), "
			+ "`MALFORMED_REQUEST` (okunamayan JSON, tekrar eden anahtar ya da metin alanına sayı), "
			+ "`UNKNOWN_PAYMENT` (`providerPaymentId` ile ödeme yok) veya `AMOUNT_MISMATCH` (`amount`/`currency` "
			+ "ödemeyle aynı değil).",
			content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON,
					schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
	@ApiResponse(responseCode = "404", description = "`NOT_FOUND`: bilinmeyen ya da etkin olmayan sağlayıcı "
			+ "(gövde okunmaz).",
			content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON,
					schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
	@ApiResponse(responseCode = "413", description = "`PAYLOAD_TOO_LARGE`: gövde sınırdan büyük (varsayılan 64 KiB; "
			+ "`app.payment.webhook.max-body-bytes`).",
			content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON,
					schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
	@ApiResponse(responseCode = "415", description = "`UNSUPPORTED_MEDIA_TYPE`: Content-Type `application/json` değil.",
			content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON,
					schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
	public ResponseEntity<Void> receive(
			@Parameter(description = "Sağlayıcı. v1'de yalnızca `mock`.",
					schema = @Schema(type = "string", allowableValues = "mock")) @PathVariable String provider,
			@Parameter(hidden = true) HttpServletRequest request)
			throws IOException, HttpMediaTypeNotSupportedException {
		PaymentProviderType type = requireActiveProvider(provider);
		requireJson(request);
		byte[] body = readBody(request);
		this.verifier.verify(request.getHeader(MockWebhookSigner.TIMESTAMP_HEADER),
				request.getHeader(MockWebhookSigner.SIGNATURE_HEADER), body);
		WebhookEvent event = validate(parse(body));
		WebhookService.Outcome outcome = handle(type, event);
		log.info("Webhook handled (provider={}, type={}, outcome={})", type.dbValue(), event.type(), outcome);
		return ResponseEntity.noContent().build();
	}

	/** Bilinmeyen ya da etkin olmayan sağlayıcının yolu da "yok" sayılır (hangi sağlayıcıların bilindiği sızmaz). */
	private PaymentProviderType requireActiveProvider(String provider) {
		if (this.activeProvider != PaymentProviderType.MOCK || !PaymentProviderType.MOCK.dbValue().equals(provider)) {
			throw new WebhookRejectedException(CommonErrorCode.NOT_FOUND);
		}
		return PaymentProviderType.MOCK;
	}

	private static void requireJson(HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
		MediaType contentType = null;
		try {
			contentType = (request.getContentType() != null) ? MediaType.parseMediaType(request.getContentType())
					: null;
		}
		catch (InvalidMediaTypeException ex) {
			// aşağıda desteklenmeyen tür olarak reddedilir
		}
		if (contentType == null || !MediaType.APPLICATION_JSON.equalsTypeAndSubtype(contentType)) {
			throw new HttpMediaTypeNotSupportedException(contentType, List.of(MediaType.APPLICATION_JSON),
					HttpMethod.POST);
		}
	}

	/** Content-Length bildirilmişse gövde hiç okunmadan; bildirilmemişse (chunked) en fazla sınır + 1 bayt okunur. */
	private byte[] readBody(HttpServletRequest request) throws IOException {
		if (request.getContentLengthLong() > this.maxBodyBytes) {
			throw new WebhookRejectedException(PaymentErrorCode.PAYLOAD_TOO_LARGE);
		}
		byte[] body = request.getInputStream().readNBytes(this.maxBodyBytes + 1);
		if (body.length > this.maxBodyBytes) {
			throw new WebhookRejectedException(PaymentErrorCode.PAYLOAD_TOO_LARGE);
		}
		return body;
	}

	/** Jackson mesajı gövdeden parça içerebilir; exception zincirlenmez. */
	private WebhookEvent parse(byte[] body) {
		WebhookEvent event;
		try {
			event = this.reader.readValue(body, WebhookEvent.class);
		}
		catch (JacksonException ex) {
			throw new WebhookRejectedException(CommonErrorCode.MALFORMED_REQUEST);
		}
		if (event == null) {
			throw new WebhookRejectedException(CommonErrorCode.MALFORMED_REQUEST);
		}
		return event;
	}

	private WebhookEvent validate(WebhookEvent event) {
		Set<ConstraintViolation<WebhookEvent>> violations = this.validator.validate(event);
		if (!violations.isEmpty()) {
			throw new ConstraintViolationException(violations);
		}
		return event;
	}

	private WebhookService.Outcome handle(PaymentProviderType provider, WebhookEvent event) {
		try {
			return this.webhookService.handle(provider, event);
		}
		catch (DataIntegrityViolationException ex) {
			if (!DbConstraints.isViolated(ex, WebhookService.EVENT_CONSTRAINT)) {
				throw ex;
			}
			return WebhookService.Outcome.DUPLICATE;
		}
	}

}
