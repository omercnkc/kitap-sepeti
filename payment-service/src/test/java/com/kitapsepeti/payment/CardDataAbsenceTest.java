package com.kitapsepeti.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kart verisi bu servise hiç girmez: (a) {@code com.kitapsepeti.payment} altındaki hiçbir sınıfın (main) alanında,
 * (b) OpenAPI dokümanındaki hiçbir özellik, parametre ya da başlık adında kart adı geçmez. (c) DB tarafı Adım 1'den
 * beri {@code PaymentSchemaConstraintsTest#noCardDataColumnsInAnyTable}'da; burada tekrarlanmaz.
 * <p>
 * Adlar kelimelere bölünerek (camelCase, {@code _}, {@code -}) karşılaştırılır: {@code cardNumber} ve
 * {@code card_holder} yakalanır, {@code company} ya da {@code expandable} yakalanmaz. Static alanlar (ör.
 * {@code MockOutcomeRule.CARD_DECLINED} ret kodu sabiti) veri taşımadığı için denetlenmez.
 */
class CardDataAbsenceTest extends ApiTestSupport {

	private static final Set<String> CARD_WORDS = Set.of("card", "pan", "cvv", "cvc", "expiry", "cardholder",
			"cardnumber");

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void noMainClassHasCardDataFields() throws Exception {
		List<Class<?>> classes = mainClasses();

		assertThat(classes).as("taranan sınıflar").hasSizeGreaterThan(50)
			.contains(com.kitapsepeti.payment.entity.Payment.class,
					com.kitapsepeti.payment.dto.request.CreatePaymentRequest.class,
					com.kitapsepeti.payment.dto.webhook.WebhookEvent.class);
		assertThat(classes.stream().flatMap(type -> cardFields(type).stream()).toList()).isEmpty();
	}

	@Test
	void openApiDocumentHasNoCardDataNames() throws Exception {
		JsonNode docs = mapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
		List<String> names = new ArrayList<>();
		collectNames(docs, "", names);

		assertThat(names).as("dokümandaki özellik/parametre/başlık adları")
			.contains("/components/schemas/PaymentResponse/properties/amount",
					"/paths/~1webhooks~1{provider}/post/parameters/X-Mock-Timestamp",
					"/paths/~1internal~1payments/post/responses/201/headers/Location");
		assertThat(names).filteredOn(name -> isCardName(name.substring(name.lastIndexOf('/') + 1))).isEmpty();
	}

	/** Negatif kontrol: denetim kart alanını gerçekten yakalar; kelime sınırı ve static istisnası beklendiği gibi. */
	@Test
	void checkCatchesCardFieldsButNotLookalikesOrStaticConstants() {
		assertThat(cardFields(FakeCardPayment.class)).containsExactlyInAnyOrder("FakeCardPayment.cardNumber",
				"FakeCardPayment.cvv", "FakeCardPayment.card_holder", "FakeCardPayment.expiryMonth",
				"FakeCardPayment.pan", "FakeCardPayment.cardholderName");
		assertThat(cardFields(Lookalikes.class)).isEmpty();
		assertThat(cardFields(CardRecord.class)).containsExactly("CardRecord.cvc");

		assertThat(isCardName("cardNumber")).isTrue();
		assertThat(isCardName("CARD_NUMBER")).isTrue();
		assertThat(isCardName("company")).isFalse();
		assertThat(isCardName("expandable")).isFalse();
		assertThat(isCardName("panel")).isFalse();
		assertThat(isCardName("discard")).isFalse();
	}

	static List<String> cardFields(Class<?> type) {
		return Arrays.stream(type.getDeclaredFields())
			.filter(field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()))
			.map(Field::getName)
			.filter(CardDataAbsenceTest::isCardName)
			.map(name -> type.getSimpleName() + "." + name)
			.toList();
	}

	static boolean isCardName(String name) {
		String[] words = name.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^A-Za-z0-9]+");
		return Arrays.stream(words).map(word -> word.toLowerCase(Locale.ROOT)).anyMatch(CARD_WORDS::contains);
	}

	/** Main çıktı dizinindeki ({@code target/classes}) tüm sınıflar; test sınıfları dahil değil. */
	private static List<Class<?>> mainClasses() throws IOException, ClassNotFoundException, URISyntaxException {
		Path root = Path.of(PaymentServiceApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		List<Class<?>> classes = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root.resolve("com/kitapsepeti/payment"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
				String name = root.relativize(file).toString().replace('\\', '/').replace('/', '.');
				classes.add(Class.forName(name.substring(0, name.length() - ".class".length()), false,
						CardDataAbsenceTest.class.getClassLoader()));
			}
		}
		return classes;
	}

	/** Şema özellikleri, parametreler ve yanıt başlıkları; JSON Pointer biçiminde, son parça ad. */
	private static void collectNames(JsonNode node, String path, List<String> out) {
		if (node.isObject()) {
			for (Map.Entry<String, JsonNode> entry : node.properties()) {
				String child = path + "/" + entry.getKey().replace("~", "~0").replace("/", "~1");
				if (entry.getKey().equals("properties") || entry.getKey().equals("headers")) {
					entry.getValue().propertyNames().forEach(name -> out.add(child + "/" + name));
				}
				collectNames(entry.getValue(), child, out);
			}
		}
		else if (node.isArray()) {
			for (JsonNode item : node) {
				if (path.endsWith("/parameters") && item.has("name")) {
					out.add(path + "/" + item.get("name").asString());
				}
				collectNames(item, path, out);
			}
		}
	}

	@SuppressWarnings("unused")
	static class FakeCardPayment {

		static final String CARD_DECLINED = "CARD_DECLINED";

		String cardNumber;

		String cvv;

		String card_holder;

		int expiryMonth;

		String pan;

		String cardholderName;

		String orderId;

	}

	@SuppressWarnings("unused")
	static class Lookalikes {

		String company;

		boolean expandable;

		String panel;

		String discard;

		String spanish;

	}

	record CardRecord(String orderId, String cvc) {
	}

}
