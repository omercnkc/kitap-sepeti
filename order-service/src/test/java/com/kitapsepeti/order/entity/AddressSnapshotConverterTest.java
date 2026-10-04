package com.kitapsepeti.order.entity;

import static com.kitapsepeti.order.entity.OrderFixtures.CITY;
import static com.kitapsepeti.order.entity.OrderFixtures.LINE1;
import static com.kitapsepeti.order.entity.OrderFixtures.PHONE;
import static com.kitapsepeti.order.entity.OrderFixtures.RECIPIENT;
import static com.kitapsepeti.order.entity.OrderFixtures.address;
import static com.kitapsepeti.order.entity.OrderFixtures.minimalAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@link AddressSnapshot} kuralları ve {@link AddressSnapshotConverter}'ın sabit JSON sözleşmesi (DB'siz). */
class AddressSnapshotConverterTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AddressSnapshotConverter converter = new AddressSnapshotConverter();

	@Test
	void writesAllEightFieldsIncludingNulls() {
		JsonNode node = JSON.readTree(converter.convertToDatabaseColumn(minimalAddress()));

		assertThat(node.propertyNames()).containsExactlyElementsOf(AddressSnapshotConverter.FIELDS);
		assertThat(node.get("recipientName").stringValue()).isEqualTo(RECIPIENT);
		assertThat(node.get("line2").isNull()).isTrue();
		assertThat(node.get("district").isNull()).isTrue();
		assertThat(node.get("postalCode").isNull()).isTrue();
		assertThat(node.get("country").stringValue()).isEqualTo("TR");
	}

	@Test
	void roundTripsFullAndMinimalAddress() {
		for (AddressSnapshot address : new AddressSnapshot[] { address(), minimalAddress() }) {
			assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(address)))
				.isEqualTo(address);
		}
	}

	@Test
	void nullMapsToNull() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}

	@Test
	void readsFieldsInAnyOrder() {
		String json = "{\"country\":\"TR\",\"postalCode\":null,\"city\":\"Ankara\",\"district\":null,\"line2\":null,"
				+ "\"line1\":\"Sokak\",\"phone\":\"555\",\"recipientName\":\"Ad\"}";

		assertThat(converter.convertToEntityAttribute(json))
			.isEqualTo(new AddressSnapshot("Ad", "555", "Sokak", null, null, "Ankara", null, "TR"));
	}

	@Test
	void unknownFieldIsRejected() {
		assertRejected(json("\"label\":\"Ev\","));
	}

	@Test
	void missingFieldIsRejected() {
		assertRejected("{\"recipientName\":\"Ad\",\"phone\":\"555\",\"line1\":\"Sokak\",\"district\":null,"
				+ "\"city\":\"Ankara\",\"postalCode\":null,\"country\":\"TR\"}");
	}

	@Test
	void duplicateFieldIsRejected() {
		assertRejected(json("\"city\":\"İzmir\","));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1", "true", "{}", "[]" })
	void nonStringValueIsRejected(String value) {
		assertRejected(json("").replace("\"line2\":null", "\"line2\":" + value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "[]", "\"text\"", "null", "{", "" })
	void nonObjectOrInvalidJsonIsRejected(String json) {
		assertRejected(json);
	}

	/** Değer kuralları kurucuda: DB'de bozulmuş değer (ör. boş şehir) okunurken de reddedilir. */
	@Test
	void storedValuesAreValidatedByTheRecord() {
		assertRejected(json("").replace("\"city\":\"Ankara\"", "\"city\":\"\""));
		assertRejected(json("").replace("\"country\":\"TR\"", "\"country\":\"tr\""));
	}

	@Test
	void errorMessagesDoNotLeakValues() {
		String json = json("\"label\":\"" + RECIPIENT + "\",").replace("\"Ad\"", "\"" + RECIPIENT + "\"");

		assertThatThrownBy(() -> converter.convertToEntityAttribute(json)).message()
			.doesNotContain(RECIPIENT)
			.doesNotContain("Ankara");
	}

	// --- AddressSnapshot ---

	@Test
	void requiredFieldsMustNotBeBlank() {
		assertThatThrownBy(() -> new AddressSnapshot(" ", PHONE, LINE1, null, null, CITY, null, "TR"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Address recipientName must not be blank");
		assertThatThrownBy(() -> new AddressSnapshot(RECIPIENT, null, LINE1, null, null, CITY, null, "TR"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AddressSnapshot(RECIPIENT, PHONE, "", null, null, CITY, null, "TR"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AddressSnapshot(RECIPIENT, PHONE, LINE1, null, null, null, null, "TR"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void lengthsFollowUserAddressColumns() {
		assertThat(new AddressSnapshot("a".repeat(120), "1".repeat(32), "l".repeat(200), "m".repeat(200),
				"d".repeat(80), "c".repeat(80), "p".repeat(16), "TR"))
			.isNotNull();
		assertThatThrownBy(() -> new AddressSnapshot("a".repeat(121), PHONE, LINE1, null, null, CITY, null, "TR"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Address recipientName must be at most 120 characters");
		assertThatThrownBy(() -> new AddressSnapshot(RECIPIENT, PHONE, LINE1, null, null, CITY, "p".repeat(17), "TR"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "tr", "TUR", "T", "" })
	void countryMustBeTwoUpperCaseLetters(String country) {
		assertThatThrownBy(() -> new AddressSnapshot(RECIPIENT, PHONE, LINE1, null, null, CITY, null, country))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void equalityIsByValue() {
		assertThat(address()).isEqualTo(address()).hasSameHashCodeAs(address());
		assertThat(address()).isNotEqualTo(minimalAddress());
	}

	/** Sekiz alanın tamamı; {@code prefix} ilk alanın önüne eklenir (ek/tekrarlanan anahtar için). */
	private static String json(String prefix) {
		return "{" + prefix + "\"recipientName\":\"Ad\",\"phone\":\"555\",\"line1\":\"Sokak\",\"line2\":null,"
				+ "\"district\":null,\"city\":\"Ankara\",\"postalCode\":null,\"country\":\"TR\"}";
	}

	private void assertRejected(String json) {
		assertThatThrownBy(() -> converter.convertToEntityAttribute(json)).isInstanceOf(IllegalArgumentException.class);
	}

}
