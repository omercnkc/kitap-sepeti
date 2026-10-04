package com.kitapsepeti.order.entity;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link AddressSnapshot} ↔ {@code orders.address_snapshot} JSON nesnesi. Hibernate'in JSON format mapper'ı yerine açık
 * eşleme: Hibernate classpath'te hangi Jackson'ı bulursa onu seçer (şu an springdoc'un getirdiği Jackson 2), bilinmeyen
 * alan davranışı da o mapper'ın varsayılanına kalır. Burada alan adları sabit ve sözleşmedir:
 * <ul>
 * <li>Yazarken sekiz alanın hepsi yazılır (boş olanlar {@code null}).</li>
 * <li>Okurken tam olarak bu sekiz alan beklenir; bilinmeyen ya da eksik alan, metin/null olmayan değer, tekrarlanan
 * anahtar → {@link IllegalArgumentException}. Değer kuralları {@link AddressSnapshot} kurucusunda.</li>
 * </ul>
 * Hata mesajları adres değerlerini içermez.
 */
@Converter
public class AddressSnapshotConverter implements AttributeConverter<AddressSnapshot, String> {

	static final List<String> FIELDS = List.of("recipientName", "phone", "line1", "line2", "district", "city",
			"postalCode", "country");

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.build();

	@Override
	public String convertToDatabaseColumn(AddressSnapshot address) {
		if (address == null) {
			return null;
		}
		ObjectNode node = MAPPER.createObjectNode();
		node.put("recipientName", address.recipientName());
		node.put("phone", address.phone());
		node.put("line1", address.line1());
		node.put("line2", address.line2());
		node.put("district", address.district());
		node.put("city", address.city());
		node.put("postalCode", address.postalCode());
		node.put("country", address.country());
		return MAPPER.writeValueAsString(node);
	}

	@Override
	public AddressSnapshot convertToEntityAttribute(String json) {
		if (json == null) {
			return null;
		}
		JsonNode node;
		try {
			node = MAPPER.readTree(json);
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException("Stored address snapshot is not valid JSON");
		}
		if (node == null || !node.isObject()) {
			throw new IllegalArgumentException("Stored address snapshot is not a JSON object");
		}
		Set<String> names = new HashSet<>(node.propertyNames());
		if (!names.equals(Set.copyOf(FIELDS))) {
			throw new IllegalArgumentException("Stored address snapshot must have exactly the fields " + FIELDS);
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (String field : FIELDS) {
			JsonNode value = node.get(field);
			if (value.isNull()) {
				values.put(field, null);
			}
			else if (value.isString()) {
				values.put(field, value.stringValue());
			}
			else {
				throw new IllegalArgumentException("Stored address snapshot field " + field + " must be a string or null");
			}
		}
		return new AddressSnapshot(values.get("recipientName"), values.get("phone"), values.get("line1"),
				values.get("line2"), values.get("district"), values.get("city"), values.get("postalCode"),
				values.get("country"));
	}

}
