package com.kitapsepeti.user.config;

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

import com.kitapsepeti.user.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /v3/api-docs} çıktısı ile {@code docs/api/user-service.openapi.json} anlamsal olarak aynı olmalı
 * (anahtar sırası ve biçim önemsiz). Uç/DTO değişip dosya güncellenmezse test kırılır.
 * {@code -Dopenapi.contract.update=true} ile çalıştırılınca karşılaştırmak yerine dosyayı yeniden yazar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

	/** Surefire çalışma dizini modül klasörüdür (user-service). */
	private static final Path CONTRACT = Path.of("..", "docs", "api", "user-service.openapi.json");

	private static final String UPDATE_PROPERTY = "openapi.contract.update";

	private static final String REGENERATE_HINT = """
			docs/api/user-service.openapi.json güncel değil. Değişiklik bilinçliyse dosyayı yeniden üretin:
			  .\\mvnw.cmd -pl user-service test "-Dtest=OpenApiContractTest" "-D%s=true"
			ve farkı gözden geçirip commit'leyin.""".formatted(UPDATE_PROPERTY);

	private static final int MAX_REPORTED_DIFFERENCES = 20;

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Test
	void generatedDocumentMatchesCommittedContract() throws Exception {
		JsonNode actual = mapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

		if (Boolean.getBoolean(UPDATE_PROPERTY)) {
			Files.writeString(CONTRACT, pretty(actual), StandardCharsets.UTF_8);
			return;
		}

		assertThat(Files.exists(CONTRACT)).as("%s bulunamadı.%n%s", CONTRACT.toAbsolutePath().normalize(), REGENERATE_HINT)
			.isTrue();
		JsonNode expected = mapper.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));

		List<String> differences = new ArrayList<>();
		diff("", expected, actual, differences);
		assertThat(differences).as("%s%nFarklı JSON yolları (ilk %d):", REGENERATE_HINT, MAX_REPORTED_DIFFERENCES).isEmpty();
	}

	/** Beklenen/gerçek ağaçları gezip farklı düğümlerin JSON Pointer yollarını toplar. */
	private static void diff(String path, JsonNode expected, JsonNode actual, List<String> out) {
		if (out.size() >= MAX_REPORTED_DIFFERENCES || expected.equals(actual)) {
			return;
		}
		if (expected.isObject() && actual.isObject()) {
			Set<String> names = new TreeSet<>(expected.propertyNames());
			names.addAll(actual.propertyNames());
			for (String name : names) {
				String child = path + "/" + name.replace("~", "~0").replace("/", "~1");
				if (!expected.has(name)) {
					out.add(child + " (dosyada yok)");
				}
				else if (!actual.has(name)) {
					out.add(child + " (üretilen dokümanda yok)");
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
		else if (out.size() < MAX_REPORTED_DIFFERENCES) {
			out.add(path.isEmpty() ? "/" : path);
		}
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
