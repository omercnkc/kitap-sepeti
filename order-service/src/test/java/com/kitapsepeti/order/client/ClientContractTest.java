package com.kitapsepeti.order.client;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kitapsepeti.order.client.cart.CartSnapshotResponse;
import com.kitapsepeti.order.client.cart.FeignCartGateway;
import com.kitapsepeti.order.client.catalog.BookLookupResponse;
import com.kitapsepeti.order.client.catalog.FeignCatalogGateway;
import com.kitapsepeti.order.client.catalog.ReservationResponse;
import com.kitapsepeti.order.client.payment.PaymentResponse;
import com.kitapsepeti.order.gateway.CatalogGateway;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.support.StubServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tüketici tarafı sözleşme testi: sağlayıcıların yayımladığı OpenAPI dokümanları ({@code docs/api/*.openapi.json},
 * yalnızca okunur) Order'ın kullandığı yolları, okuduğu alanları (var + required + tip), durum değerlerini ve eşlediği
 * hata kodlarını hâlâ sağlıyor mu; Order'ın GERÇEKTEN gönderdiği gövdeler (stub'ın aldığı) istek şemasına uyuyor mu.
 * Order'ın kullandığı her uç OpenAPI'de tanımlı; markdown'dan kopyalanan fixture gerekmedi.
 */
class ClientContractTest extends ClientTestSupport {

	private static final String SCHEMA_PREFIX = "#/components/schemas/";

	private static final String JSON = "application~1json";

	private static final String PROBLEM_JSON = "application~1problem+json";

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private static final Path DOCS = Path.of("..", "docs", "api");

	private static JsonNode cart() {
		return load(DOCS.resolve("cart-service.openapi.json"));
	}

	private static JsonNode catalog() {
		return load(DOCS.resolve("catalog-service.openapi.json"));
	}

	private static JsonNode payment() {
		return load(DOCS.resolve("payment-service.openapi.json"));
	}

	@Test
	void cartContractProvidesWhatOrderUses() {
		assertThat(cartViolations(cart())).isEmpty();
	}

	@Test
	void catalogContractProvidesWhatOrderUses() {
		assertThat(catalogViolations(catalog())).isEmpty();
	}

	@Test
	void paymentContractProvidesWhatOrderUses() {
		assertThat(paymentViolations(payment())).isEmpty();
	}

	/** Gönderilen gövdeler stub'dan okunur: Feign'in gerçek serileştirmesi (Boot JsonMapper) doğrulanır. */
	@Test
	void sentBodiesMatchRequestSchemas() {
		STUBS.forEach(stub -> stub.server().stubFor(post(anyUrl()).willReturn(problem(500, "INTERNAL_ERROR"))));
		UUID orderId = UUID.randomUUID();

		this.cartGateway.snapshot(UUID.randomUUID());
		this.catalogGateway.reserve(orderId, List.of(new StockLine(UUID.randomUUID(), 99), new StockLine(UUID.randomUUID(), 1)));
		this.paymentGateway.initiate(orderId, UUID.randomUUID(), new BigDecimal("1234567890.05"), "TRY");
		this.paymentGateway.initiate(orderId, UUID.randomUUID(), new BigDecimal("0.01"), "TRY");

		assertThat(errors(cart(), "CartSnapshotRequest", sent(CART, "/internal/cart/snapshot").getFirst())).isEmpty();
		assertThat(errors(catalog(), "ReserveStockRequest", sent(CATALOG, "/internal/stock/reservations").getFirst()))
			.isEmpty();
		assertThat(sent(PAYMENT, "/internal/payments")).hasSize(2)
			.allSatisfy(body -> assertThat(errors(payment(), "CreatePaymentRequest", body)).isEmpty());
	}

	// --- mutasyonlar: kontroller gerçekten kırılıyor mu ---

	@Test
	void renamedFieldIsDetected() {
		JsonNode doc = catalog();
		ObjectNode properties = (ObjectNode) doc.at("/components/schemas/ReservationResponse/properties");
		properties.set("expiry", properties.remove("expiresAt"));

		assertThat(catalogViolations(doc)).contains("ReservationResponse.expiresAt: alan yok");
	}

	@Test
	void optionalOrRetypedFieldIsDetected() {
		JsonNode doc = payment();
		removeRequired(doc, "PaymentResponse", "paymentId");
		JsonNode catalogDoc = catalog();
		((ObjectNode) catalogDoc.at("/components/schemas/BookSummaryResponse/properties/priceAmount")).put("type",
				"string");

		assertThat(paymentViolations(doc)).contains("PaymentResponse.paymentId: required değil");
		assertThat(catalogViolations(catalogDoc)).contains("BookSummaryResponse.priceAmount: tip number değil");
	}

	@Test
	void newStatusValueIsDetected() {
		JsonNode doc = catalog();
		((ArrayNode) doc.at("/components/schemas/ReservationResponse/properties/status/enum")).add("mixed");

		assertThat(catalogViolations(doc)).anySatisfy(v -> assertThat(v).startsWith("ReservationResponse.status enum"));
	}

	@Test
	void securedLookupAndDroppedProblemCodeAreDetected() {
		JsonNode doc = catalog();
		((ObjectNode) doc.at("/paths/~1api~1books~1lookup/get")).set("security",
				JSON_MAPPER.readTree("[{\"bearerAuth\":[]}]"));
		ArrayNode codes = (ArrayNode) doc.at("/components/schemas/Problem/properties/code/enum");
		for (int i = 0; i < codes.size(); i++) {
			if ("RESERVATION_RELEASED".equals(codes.get(i).asString())) {
				codes.remove(i);
			}
		}

		assertThat(catalogViolations(doc)).contains("GET /api/books/lookup public değil (Order anahtar göndermez)",
				"Problem.code RESERVATION_RELEASED yok");
	}

	@Test
	void requestValidatorRejectsBrokenBodies() {
		JsonNode doc = payment();
		String valid = """
				{"orderId":"%s","userId":"%s","amount":10.50,"currency":"TRY"}""".formatted(UUID.randomUUID(),
				UUID.randomUUID());
		assertThat(errors(doc, "CreatePaymentRequest", JSON_MAPPER.readTree(valid))).isEmpty();

		assertThat(errors(doc, "CreatePaymentRequest", JSON_MAPPER.readTree(valid.replace("\"TRY\"", "\"try\""))))
			.containsExactly("currency: pattern ^[A-Z]{3}$");
		assertThat(errors(doc, "CreatePaymentRequest", JSON_MAPPER.readTree(valid.replace("10.50", "\"10.50\""))))
			.containsExactly("amount: number değil");
		assertThat(errors(doc, "CreatePaymentRequest",
				JSON_MAPPER.readTree("{\"userId\":\"" + UUID.randomUUID() + "\",\"amount\":0,\"currency\":\"TRY\",\"x\":1}")))
			.containsExactlyInAnyOrder("orderId: zorunlu alan yok", "amount: minimum 0.01", "x: şemada yok");
		assertThat(errors(catalog(), "ReserveStockRequest", JSON_MAPPER.readTree("""
				{"orderId":"nope","items":[{"bookId":"%s","quantity":101}]}""".formatted(UUID.randomUUID()))))
			.containsExactlyInAnyOrder("orderId: uuid değil", "items[0].quantity: maximum 100");
	}

	@Test
	void missingContractFileFailsWithClearMessage() {
		assertThatThrownBy(() -> load(DOCS.resolve("yok.openapi.json"))).isInstanceOf(AssertionError.class)
			.hasMessageContaining("Sözleşme dosyası bulunamadı")
			.hasMessageContaining("yok.openapi.json");
	}

	// --- servis kontrolleri ---

	private static List<String> cartViolations(JsonNode doc) {
		List<String> violations = new ArrayList<>();
		JsonNode snapshot = operation(doc, "POST", "/internal/cart/snapshot", violations);
		if (snapshot == null) {
			return violations;
		}
		requireInternal(snapshot, "POST /internal/cart/snapshot", violations);
		requireRequestSchema(snapshot, "CartSnapshotRequest", violations);
		checkRead(doc, successSchema(snapshot, "200", violations), CartSnapshotResponse.class, violations);
		long max = schema(doc, "CartSnapshotItem").at("/properties/quantity/maximum").asLong(-1);
		if (max != FeignCartGateway.MAX_QUANTITY) {
			violations.add("CartSnapshotItem.quantity maximum " + max + " != " + FeignCartGateway.MAX_QUANTITY);
		}
		return violations;
	}

	private static List<String> catalogViolations(JsonNode doc) {
		List<String> violations = new ArrayList<>();
		JsonNode lookup = operation(doc, "GET", "/api/books/lookup", violations);
		if (lookup != null) {
			JsonNode security = lookup.path("security");
			if (!security.isArray() || !security.isEmpty()) {
				violations.add("GET /api/books/lookup public değil (Order anahtar göndermez)");
			}
			checkRead(doc, successSchema(lookup, "200", violations), BookLookupResponse.class, violations);
			JsonNode ids = parameter(lookup, "ids").path("schema");
			if (!isType(ids, "array") || ids.path("maxItems").asInt(0) < CatalogGateway.MAX_BOOKS) {
				violations.add("lookup ids: en az " + CatalogGateway.MAX_BOOKS + " elemanlı dizi değil");
			}
		}
		JsonNode reserve = operation(doc, "POST", "/internal/stock/reservations", violations);
		if (reserve != null) {
			requireInternal(reserve, "POST /internal/stock/reservations", violations);
			requireRequestSchema(reserve, "ReserveStockRequest", violations);
			checkRead(doc, successSchema(reserve, "201", violations), ReservationResponse.class, violations);
			checkRead(doc, successSchema(reserve, "200", violations), ReservationResponse.class, violations);
			JsonNode conflict = resolveAllOf(doc, reserve.at("/responses/409/content/" + PROBLEM_JSON + "/schema"));
			JsonNode bookIds = conflict.path("properties").path("bookIds");
			if (!isType(bookIds, "array") || !"uuid".equals(bookIds.path("items").path("format").asString(null))) {
				violations.add("reserve 409 bookIds: uuid dizisi değil");
			}
			JsonNode items = schema(doc, "ReserveStockRequest").path("properties").path("items");
			if (items.path("maxItems").asInt(0) < CatalogGateway.MAX_BOOKS) {
				violations.add("ReserveStockRequest.items maxItems < " + CatalogGateway.MAX_BOOKS);
			}
			long max = schema(doc, "ReserveStockItem").at("/properties/quantity/maximum").asLong(-1);
			if (max != FeignCatalogGateway.MAX_QUANTITY || max < FeignCartGateway.MAX_QUANTITY) {
				violations.add("ReserveStockItem.quantity maximum " + max);
			}
		}
		for (String action : List.of("commit", "release")) {
			String path = "/internal/stock/reservations/{orderId}/" + action;
			JsonNode operation = operation(doc, "POST", path, violations);
			if (operation != null) {
				requireInternal(operation, "POST " + path, violations);
				checkRead(doc, successSchema(operation, "200", violations), ReservationResponse.class, violations);
				for (String status : List.of("404", "409")) {
					if (operation.path("responses").path(status).isMissingNode()) {
						violations.add("POST " + path + " " + status + " tanımlı değil");
					}
				}
			}
		}
		requireEnum(doc, schema(doc, "ReservationResponse").path("properties").path("status"),
				"ReservationResponse.status", Set.of("held", "committed", "released"), violations);
		requireProblemCodes(doc, List.of("INSUFFICIENT_STOCK", "BOOK_NOT_AVAILABLE", "RESERVATION_MISMATCH",
				"RESERVATION_RELEASED", "RESERVATION_COMMITTED", "RESOURCE_NOT_FOUND"), violations);
		return violations;
	}

	private static List<String> paymentViolations(JsonNode doc) {
		List<String> violations = new ArrayList<>();
		JsonNode create = operation(doc, "POST", "/internal/payments", violations);
		if (create == null) {
			return violations;
		}
		requireInternal(create, "POST /internal/payments", violations);
		requireRequestSchema(create, "CreatePaymentRequest", violations);
		checkRead(doc, successSchema(create, "201", violations), PaymentResponse.class, violations);
		checkRead(doc, successSchema(create, "200", violations), PaymentResponse.class, violations);
		for (String status : List.of("409", "503")) {
			if (create.path("responses").path(status).isMissingNode()) {
				violations.add("POST /internal/payments " + status + " tanımlı değil");
			}
		}
		requireEnum(doc, schema(doc, "PaymentResponse").path("properties").path("status"), "PaymentResponse.status",
				Set.of("initiated", "succeeded", "failed"), violations);
		requireProblemCodes(doc, List.of("PAYMENT_ORDER_MISMATCH", "PAYMENT_PROVIDER_UNAVAILABLE"), violations);
		return violations;
	}

	// --- okunan DTO ↔ yanıt şeması ---

	/** DTO'nun her alanı şemada var, required, DTO'da da {@code required = true} ve tipi uyumlu (iç içe kayıtlar dahil). */
	private static void checkRead(JsonNode doc, String schemaName, Class<?> dto, List<String> violations) {
		JsonNode schema = schema(doc, schemaName);
		if (schema.isMissingNode()) {
			violations.add(schemaName + ": şema yok");
			return;
		}
		List<String> required = required(schema);
		for (RecordComponent component : dto.getRecordComponents()) {
			String name = component.getName();
			String location = schemaName + "." + name;
			JsonNode property = schema.path("properties").path(name);
			if (property.isMissingNode()) {
				violations.add(location + ": alan yok");
				continue;
			}
			JsonProperty annotation = component.getAccessor().getAnnotation(JsonProperty.class);
			if (annotation == null || !annotation.required()) {
				violations.add(location + ": DTO'da required = true değil");
			}
			if (!required.contains(name)) {
				violations.add(location + ": required değil");
			}
			JsonNode resolved = resolve(doc, property);
			OpenApiType expected = OpenApiType.of(component.getType());
			if (!isType(resolved, expected.type())) {
				violations.add(location + ": tip " + expected.type() + " değil");
			}
			else if (expected.format() != null && !expected.format().equals(resolved.path("format").asString(null))) {
				violations.add(location + ": format " + expected.format() + " değil");
			}
			if (component.getType() == List.class) {
				Class<?> element = (Class<?>) ((ParameterizedType) component.getGenericType()).getActualTypeArguments()[0];
				if (element.isRecord()) {
					checkRead(doc, refName(property.path("items")), element, violations);
				}
			}
		}
	}

	private record OpenApiType(String type, String format) {

		static OpenApiType of(Class<?> type) {
			Map<Class<?>, OpenApiType> types = Map.of(UUID.class, new OpenApiType("string", "uuid"), String.class,
					new OpenApiType("string", null), BigDecimal.class, new OpenApiType("number", null), Integer.class,
					new OpenApiType("integer", null), Boolean.class, new OpenApiType("boolean", null), Instant.class,
					new OpenApiType("string", "date-time"), List.class, new OpenApiType("array", null));
			OpenApiType mapped = types.get(type);
			if (mapped == null) {
				throw new AssertionError("DTO alan tipi için OpenAPI eşlemesi yok: " + type);
			}
			return mapped;
		}

	}

	// --- gönderilen gövde ↔ istek şeması (küçük doğrulayıcı) ---

	private static List<String> errors(JsonNode doc, String schemaName, JsonNode body) {
		List<String> errors = new ArrayList<>();
		validate(doc, schema(doc, schemaName), body, "", errors);
		return errors;
	}

	private static void validate(JsonNode doc, JsonNode schemaNode, JsonNode value, String path, List<String> errors) {
		JsonNode schema = resolve(doc, schemaNode);
		String at = path.isEmpty() ? "$" : path;
		if (value == null || value.isNull()) {
			if (!isType(schema, "null")) {
				errors.add(at + ": null");
			}
			return;
		}
		if (isType(schema, "object")) {
			if (!value.isObject()) {
				errors.add(at + ": object değil");
				return;
			}
			for (String name : required(schema)) {
				if (!value.has(name)) {
					errors.add(child(path, name) + ": zorunlu alan yok");
				}
			}
			for (Map.Entry<String, JsonNode> field : value.properties()) {
				JsonNode property = schema.path("properties").path(field.getKey());
				if (property.isMissingNode()) {
					errors.add(child(path, field.getKey()) + ": şemada yok");
				}
				else {
					validate(doc, property, field.getValue(), child(path, field.getKey()), errors);
				}
			}
		}
		else if (isType(schema, "array")) {
			if (!value.isArray()) {
				errors.add(at + ": array değil");
				return;
			}
			if (value.size() < schema.path("minItems").asInt(0)
					|| value.size() > schema.path("maxItems").asInt(Integer.MAX_VALUE)) {
				errors.add(at + ": eleman sayısı sınır dışı");
			}
			for (int i = 0; i < value.size(); i++) {
				validate(doc, schema.path("items"), value.get(i), path + "[" + i + "]", errors);
			}
		}
		else if (isType(schema, "string")) {
			if (!value.isString()) {
				errors.add(at + ": string değil");
				return;
			}
			if ("uuid".equals(schema.path("format").asString(null)) && !isUuid(value.stringValue())) {
				errors.add(at + ": uuid değil");
			}
			String pattern = schema.path("pattern").asString(null);
			if (pattern != null && !Pattern.compile(pattern).matcher(value.stringValue()).find()) {
				errors.add(at + ": pattern " + pattern);
			}
		}
		else if (isType(schema, "integer") || isType(schema, "number")) {
			boolean integer = isType(schema, "integer");
			if (integer ? !value.isIntegralNumber() : !value.isNumber()) {
				errors.add(at + ": " + (integer ? "integer" : "number") + " değil");
				return;
			}
			BigDecimal number = value.decimalValue();
			if (schema.has("minimum") && number.compareTo(schema.path("minimum").decimalValue()) < 0) {
				errors.add(at + ": minimum " + schema.path("minimum").asString());
			}
			if (schema.has("maximum") && number.compareTo(schema.path("maximum").decimalValue()) > 0) {
				errors.add(at + ": maximum " + schema.path("maximum").asString());
			}
		}
		else if (isType(schema, "boolean") && !value.isBoolean()) {
			errors.add(at + ": boolean değil");
		}
	}

	private static String child(String path, String name) {
		return path.isEmpty() ? name : path + "." + name;
	}

	private static boolean isUuid(String value) {
		try {
			return UUID.fromString(value).toString().equalsIgnoreCase(value);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static List<JsonNode> sent(StubServer stub, String path) {
		return stub.server()
			.findAll(postRequestedFor(urlEqualTo(path)))
			.stream()
			.map(request -> JSON_MAPPER.readTree(request.getBodyAsString()))
			.toList();
	}

	// --- OpenAPI yardımcıları ---

	private static JsonNode operation(JsonNode doc, String method, String path, List<String> violations) {
		JsonNode operation = doc.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
		if (operation.isMissingNode()) {
			violations.add(method + " " + path + " yok");
			return null;
		}
		return operation;
	}

	private static void requireInternal(JsonNode operation, String name, List<String> violations) {
		if (operation.path("security").findValue("internalApiKey") == null) {
			violations.add(name + ": internalApiKey güvenliği yok");
		}
	}

	private static void requireRequestSchema(JsonNode operation, String expected, List<String> violations) {
		String actual = refName(operation.at("/requestBody/content/" + JSON + "/schema"));
		if (!expected.equals(actual)) {
			violations.add("istek şeması " + actual + " != " + expected);
		}
	}

	private static String successSchema(JsonNode operation, String status, List<String> violations) {
		String name = refName(operation.at("/responses/" + status + "/content/" + JSON + "/schema"));
		if (name.equals("<şema yok>")) {
			violations.add(operation.path("operationId").asString("?") + " " + status + " yanıt şeması yok");
		}
		return name;
	}

	private static void requireEnum(JsonNode doc, JsonNode property, String name, Set<String> expected,
			List<String> violations) {
		Set<String> actual = new HashSet<>();
		resolve(doc, property).path("enum").forEach(value -> actual.add(value.asString()));
		if (!actual.equals(expected)) {
			violations.add(name + " enum " + actual + " != " + expected);
		}
	}

	private static void requireProblemCodes(JsonNode doc, List<String> codes, List<String> violations) {
		List<String> actual = new ArrayList<>();
		schema(doc, "Problem").at("/properties/code/enum").forEach(value -> actual.add(value.asString()));
		for (String code : codes) {
			if (!actual.contains(code)) {
				violations.add("Problem.code " + code + " yok");
			}
		}
	}

	private static JsonNode parameter(JsonNode operation, String name) {
		for (JsonNode parameter : operation.path("parameters")) {
			if (name.equals(parameter.path("name").asString(null))) {
				return parameter;
			}
		}
		return MissingNode.getInstance();
	}

	private static JsonNode resolve(JsonNode doc, JsonNode node) {
		String ref = node.path("$ref").asString(null);
		return (ref != null && ref.startsWith(SCHEMA_PREFIX)) ? schema(doc, ref.substring(SCHEMA_PREFIX.length()))
				: node;
	}

	/** {@code allOf} parçalarının özelliklerini birleştirir (StockUnavailableProblem = Problem + bookIds). */
	private static JsonNode resolveAllOf(JsonNode doc, JsonNode node) {
		JsonNode schema = resolve(doc, node);
		if (!schema.has("allOf")) {
			return schema;
		}
		ObjectNode merged = JSON_MAPPER.createObjectNode();
		ObjectNode properties = merged.putObject("properties");
		for (JsonNode part : schema.path("allOf")) {
			resolveAllOf(doc, part).path("properties").properties().forEach(e -> properties.set(e.getKey(), e.getValue()));
		}
		return merged;
	}

	private static String refName(JsonNode schema) {
		String ref = schema.path("$ref").asString("");
		return ref.startsWith(SCHEMA_PREFIX) ? ref.substring(SCHEMA_PREFIX.length()) : "<şema yok>";
	}

	private static JsonNode schema(JsonNode doc, String name) {
		return doc.path("components").path("schemas").path(name);
	}

	private static List<String> required(JsonNode schema) {
		List<String> required = new ArrayList<>();
		schema.path("required").forEach(node -> required.add(node.asString()));
		return required;
	}

	/** OpenAPI 3.1'de tip tek değer ya da dizi ({@code ["string", "null"]}) olabilir. */
	private static boolean isType(JsonNode schema, String type) {
		JsonNode actual = schema.path("type");
		if (actual.isArray()) {
			for (JsonNode value : actual) {
				if (type.equals(value.asString())) {
					return true;
				}
			}
			return false;
		}
		return type.equals(actual.asString(null));
	}

	private static void removeRequired(JsonNode doc, String schemaName, String field) {
		ArrayNode required = (ArrayNode) schema(doc, schemaName).path("required");
		for (int i = 0; i < required.size(); i++) {
			if (field.equals(required.get(i).asString())) {
				required.remove(i);
				return;
			}
		}
	}

	private static JsonNode load(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		assertThat(Files.isRegularFile(absolute))
			.as("Sözleşme dosyası bulunamadı: %s (order-service modül dizininden ../docs/api/ beklenir)", absolute)
			.isTrue();
		try {
			return JSON_MAPPER.readTree(Files.readString(absolute));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
