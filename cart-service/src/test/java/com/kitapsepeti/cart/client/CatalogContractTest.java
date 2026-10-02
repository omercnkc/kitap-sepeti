package com.kitapsepeti.cart.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tüketici tarafı sözleşme testi: Catalog'un yayımladığı OpenAPI dokümanı ({@code docs/api/catalog-service.openapi.json},
 * yalnızca okunur) Cart'ın {@link CatalogClient} ile kullandığı yolları ve alanları hâlâ sağlıyor mu. Catalog bir alanı
 * yeniden adlandırır, opsiyonel yapar, tipini ya da toplu okuma sınırını değiştirirse burada kırılır.
 */
class CatalogContractTest {

	private static final Path CONTRACT = Path.of("..", "docs", "api", "catalog-service.openapi.json");

	private static final String SCHEMA_PREFIX = "#/components/schemas/";

	private static final String JSON = "application~1json";

	/** {@link CatalogBook} alanları; coverUrl dışındakileri Cart zorunlu sayar. */
	private static final List<Field> USED_FIELDS = List.of(
			new Field("id", "string", "uuid", true),
			new Field("title", "string", null, true),
			new Field("priceAmount", "number", null, true),
			new Field("currency", "string", null, true),
			new Field("coverUrl", "string", null, false),
			new Field("inStock", "boolean", null, true));

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Test
	void catalogContractProvidesWhatCartUses() {
		assertThat(violations(load(CONTRACT), cartMaxLines())).isEmpty();
	}

	@Test
	void renamedFieldIsDetected() {
		JsonNode doc = load(CONTRACT);
		ObjectNode properties = (ObjectNode) doc.at("/components/schemas/BookSummaryResponse/properties");
		properties.set("price", properties.remove("priceAmount"));
		replaceRequired(doc, "BookSummaryResponse", "priceAmount", "price");

		assertThat(violations(doc, cartMaxLines()))
			.contains("BookSummaryResponse.priceAmount: alan yok");
	}

	@Test
	void optionalOrRetypedFieldIsDetected() {
		JsonNode doc = load(CONTRACT);
		replaceRequired(doc, "BookDetailResponse", "inStock", null);
		((ObjectNode) doc.at("/components/schemas/BookDetailResponse/properties/priceAmount")).put("type", "string");

		assertThat(violations(doc, cartMaxLines())).contains("BookDetailResponse.inStock: required değil",
				"BookDetailResponse.priceAmount: tip number değil");
	}

	@Test
	void lowerLookupLimitIsDetected() {
		JsonNode doc = load(CONTRACT);
		((ObjectNode) idsParameter(doc).path("schema")).put("maxItems", 20);

		assertThat(violations(doc, cartMaxLines())).anySatisfy(v -> assertThat(v).startsWith("lookup ids maxItems 20"));
	}

	@Test
	void missingContractFileFailsWithClearMessage() {
		Path missing = Path.of("..", "docs", "api", "yok-catalog.openapi.json");

		assertThatThrownBy(() -> load(missing)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("Catalog sözleşme dosyası bulunamadı")
			.hasMessageContaining("yok-catalog.openapi.json");
	}

	private static List<String> violations(JsonNode doc, int cartMaxLines) {
		List<String> violations = new ArrayList<>();
		JsonNode getBook = doc.at("/paths/~1api~1books~1{id}/get");
		JsonNode lookup = doc.at("/paths/~1api~1books~1lookup/get");
		if (getBook.isMissingNode()) {
			violations.add("GET /api/books/{id} yok");
		}
		else {
			checkBookSchema(doc, successSchemaName(getBook), violations);
		}
		if (lookup.isMissingNode()) {
			violations.add("GET /api/books/lookup yok");
			return violations;
		}
		String lookupSchema = successSchemaName(lookup);
		JsonNode items = schema(doc, lookupSchema).path("properties").path("items");
		if (!isType(items, "array") || !requiredFields(doc, lookupSchema).contains("items")) {
			violations.add(lookupSchema + ".items: zorunlu dizi değil");
		}
		checkBookSchema(doc, refName(items.path("items")), violations);

		JsonNode ids = idsParameter(doc).path("schema");
		int maxItems = ids.path("maxItems").asInt(0);
		if (!isType(ids, "array") || !"uuid".equals(ids.path("items").path("format").asString(null))) {
			violations.add("lookup ids: uuid dizisi değil");
		}
		if (maxItems < cartMaxLines || maxItems < CatalogGateway.MAX_LOOKUP_IDS) {
			violations.add("lookup ids maxItems " + maxItems + " < cart max-lines " + cartMaxLines + " / gateway "
					+ CatalogGateway.MAX_LOOKUP_IDS);
		}
		if (cartMaxLines > CatalogGateway.MAX_LOOKUP_IDS) {
			violations.add("cart max-lines " + cartMaxLines + " > gateway " + CatalogGateway.MAX_LOOKUP_IDS);
		}
		return violations;
	}

	private static void checkBookSchema(JsonNode doc, String name, List<String> violations) {
		JsonNode properties = schema(doc, name).path("properties");
		List<String> required = requiredFields(doc, name);
		for (Field field : USED_FIELDS) {
			JsonNode property = properties.path(field.name());
			String location = name + "." + field.name();
			if (property.isMissingNode()) {
				violations.add(location + ": alan yok");
				continue;
			}
			if (!isType(property, field.type())) {
				violations.add(location + ": tip " + field.type() + " değil");
			}
			if (field.format() != null && !field.format().equals(property.path("format").asString(null))) {
				violations.add(location + ": format " + field.format() + " değil");
			}
			if (field.required() && !required.contains(field.name())) {
				violations.add(location + ": required değil");
			}
		}
	}

	private static String successSchemaName(JsonNode operation) {
		return refName(operation.at("/responses/200/content/" + JSON + "/schema"));
	}

	private static String refName(JsonNode schema) {
		String ref = schema.path("$ref").asString("");
		return ref.startsWith(SCHEMA_PREFIX) ? ref.substring(SCHEMA_PREFIX.length()) : "<şema yok>";
	}

	private static JsonNode schema(JsonNode doc, String name) {
		return doc.path("components").path("schemas").path(name);
	}

	private static List<String> requiredFields(JsonNode doc, String name) {
		List<String> required = new ArrayList<>();
		schema(doc, name).path("required").forEach(node -> required.add(node.asString()));
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

	private static JsonNode idsParameter(JsonNode doc) {
		for (JsonNode parameter : doc.at("/paths/~1api~1books~1lookup/get/parameters")) {
			if ("ids".equals(parameter.path("name").asString(null)) && "query".equals(parameter.path("in").asString(null))) {
				return parameter;
			}
		}
		return MissingNode.getInstance();
	}

	private static void replaceRequired(JsonNode doc, String schemaName, String field, String replacement) {
		ArrayNode required = (ArrayNode) schema(doc, schemaName).path("required");
		for (int i = 0; i < required.size(); i++) {
			if (field.equals(required.get(i).asString())) {
				required.remove(i);
				if (replacement != null) {
					required.add(replacement);
				}
				return;
			}
		}
	}

	private static JsonNode load(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		assertThat(Files.isRegularFile(absolute))
			.as("Catalog sözleşme dosyası bulunamadı: %s (cart-service modül dizininden ../docs/api/ beklenir)", absolute)
			.isTrue();
		try {
			return JSON_MAPPER.readTree(Files.readString(absolute));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static int cartMaxLines() {
		try {
			Object value = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
				.getFirst()
				.getProperty("app.cart.max-lines");
			assertThat(value).as("application.yml app.cart.max-lines").isNotNull();
			return Integer.parseInt(value.toString());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private record Field(String name, String type, String format, boolean required) {
	}

}
