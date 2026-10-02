package com.kitapsepeti.cart.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.kitapsepeti.cart.ApiTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /v3/api-docs} çıktısı ile {@code docs/api/cart-service.openapi.json} anlamsal olarak aynı olmalı
 * (anahtar sırası ve biçim önemsiz). Uç/DTO değişip dosya güncellenmezse test kırılır; farklar yol + beklenen/
 * üretilen değer olarak raporlanır ve üretilen doküman {@code target/openapi/} altına yazılır.
 * {@code -Dopenapi.contract.update=true} ile çalıştırılınca karşılaştırmak yerine dosyayı yeniden yazar.
 */
class OpenApiContractTest extends ApiTestSupport {

	/** Surefire çalışma dizini modül klasörüdür (cart-service). */
	private static final Path CONTRACT = Path.of("..", "docs", "api", "cart-service.openapi.json");

	private static final Path ACTUAL_OUTPUT = Path.of("target", "openapi", "cart-service.openapi.json");

	private static final String UPDATE_PROPERTY = "openapi.contract.update";

	private static final String REGENERATE_HINT = """
			docs/api/cart-service.openapi.json güncel değil. Üretilen doküman: %s
			Değişiklik bilinçliyse dosyayı yeniden üretin:
			  .\\mvnw.cmd -pl cart-service test "-Dtest=OpenApiContractTest" "-D%s=true"
			ve farkı gözden geçirip commit'leyin.""".formatted(ACTUAL_OUTPUT.toAbsolutePath().normalize(),
			UPDATE_PROPERTY);

	private static final int MAX_REPORTED_DIFFERENCES = 20;

	private static final int MAX_VALUE_LENGTH = 160;

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void generatedDocumentMatchesCommittedContract() throws Exception {
		JsonNode actual = mapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

		if (Boolean.getBoolean(UPDATE_PROPERTY)) {
			Files.writeString(CONTRACT, pretty(actual), StandardCharsets.UTF_8);
			return;
		}

		List<String> differences = new ArrayList<>();
		if (Files.exists(CONTRACT)) {
			diff("", mapper.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8)), actual, differences);
		}
		else {
			differences.add(CONTRACT.toAbsolutePath().normalize() + " bulunamadı");
		}
		if (!differences.isEmpty()) {
			Files.createDirectories(ACTUAL_OUTPUT.getParent());
			Files.writeString(ACTUAL_OUTPUT, pretty(actual), StandardCharsets.UTF_8);
		}
		assertThat(differences).as("%s%nFarklar (ilk %d):", REGENERATE_HINT, MAX_REPORTED_DIFFERENCES).isEmpty();
	}

	/** Beklenen/gerçek ağaçları gezip farklı düğümleri JSON Pointer yolu ve iki tarafın değeriyle toplar. */
	private void diff(String path, JsonNode expected, JsonNode actual, List<String> out) {
		if (out.size() >= MAX_REPORTED_DIFFERENCES || expected.equals(actual)) {
			return;
		}
		if (expected.isObject() && actual.isObject()) {
			Set<String> names = new TreeSet<>(expected.propertyNames());
			names.addAll(actual.propertyNames());
			for (String name : names) {
				String child = path + "/" + name.replace("~", "~0").replace("/", "~1");
				if (!expected.has(name)) {
					report(out, child, "dosyada yok", "üretilen: " + abbreviate(actual.get(name)));
				}
				else if (!actual.has(name)) {
					report(out, child, "üretilen dokümanda yok", "dosyada: " + abbreviate(expected.get(name)));
				}
				else {
					diff(child, expected.get(name), actual.get(name), out);
				}
			}
		}
		else if (expected.isArray() && actual.isArray() && expected.size() == actual.size()) {
			for (int i = 0; i < expected.size(); i++) {
				diff(path + "/" + i, expected.get(i), actual.get(i), out);
			}
		}
		else {
			report(out, path.isEmpty() ? "/" : path, "değer farklı",
					"dosyada: " + abbreviate(expected) + System.lineSeparator() + "      üretilen: " + abbreviate(actual));
		}
	}

	private static void report(List<String> out, String path, String kind, String detail) {
		if (out.size() < MAX_REPORTED_DIFFERENCES) {
			out.add("%s (%s)%n      %s".formatted(path, kind, detail));
		}
	}

	private String abbreviate(JsonNode node) {
		String text = mapper.writeValueAsString(node);
		return text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH) + "…";
	}

	/** 2 boşluk girinti, {@code "ad": değer}, LF satır sonu; böylece dosya diff'leri okunur kalır. */
	private String pretty(JsonNode node) {
		DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
		DefaultPrettyPrinter printer = new DefaultPrettyPrinter(Separators.createDefaultInstance()
			.withObjectNameValueSpacing(Separators.Spacing.AFTER)
			.withObjectEmptySeparator("")
			.withArrayEmptySeparator(""))
			.withObjectIndenter(indenter)
			.withArrayIndenter(indenter);
		return mapper.writer().with(printer).writeValueAsString(node) + "\n";
	}

}
