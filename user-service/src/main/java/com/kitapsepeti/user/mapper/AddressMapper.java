package com.kitapsepeti.user.mapper;

import com.kitapsepeti.user.dto.request.CreateAddressRequest;
import com.kitapsepeti.user.dto.request.UpdateAddressRequest;
import com.kitapsepeti.user.dto.response.AddressResponse;
import com.kitapsepeti.user.entity.Address;
import com.kitapsepeti.user.entity.User;
import com.kitapsepeti.user.validation.TrPhones;

/**
 * {@link Address} ↔ DTO dönüşümleri. Varsayılan adres ({@code isDefault}) kuralları burada DEĞİL,
 * AddressService'te uygulanır.
 */
public final class AddressMapper {

	private static final String DEFAULT_COUNTRY = "TR";

	private AddressMapper() {
	}

	public static AddressResponse toResponse(Address address) {
		return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
				address.getPhone(), address.getLine1(), address.getLine2(), address.getDistrict(), address.getCity(),
				address.getPostalCode(), address.getCountry(), address.isDefault(), address.getCreatedAt());
	}

	public static Address toEntity(User user, CreateAddressRequest request) {
		Address address = new Address(user, request.recipientName(), TrPhones.toCanonicalOrNull(request.phone()),
				request.line1(), request.city());
		address.setLabel(blankToNull(request.label()));
		address.setLine2(blankToNull(request.line2()));
		address.setDistrict(blankToNull(request.district()));
		address.setPostalCode(blankToNull(request.postalCode()));
		address.setCountry((request.country() != null) ? request.country() : DEFAULT_COUNTRY);
		return address;
	}

	/** null alanlar değiştirilmez; opsiyonel alanlarda boş metin alanı siler. */
	public static void applyUpdate(Address address, UpdateAddressRequest request) {
		address.setLabel(optional(address.getLabel(), request.label()));
		address.setLine2(optional(address.getLine2(), request.line2()));
		address.setDistrict(optional(address.getDistrict(), request.district()));
		address.setPostalCode(optional(address.getPostalCode(), request.postalCode()));
		if (request.recipientName() != null) {
			address.setRecipientName(request.recipientName());
		}
		if (request.phone() != null) {
			address.setPhone(TrPhones.toCanonicalOrNull(request.phone()));
		}
		if (request.line1() != null) {
			address.setLine1(request.line1());
		}
		if (request.city() != null) {
			address.setCity(request.city());
		}
		if (request.country() != null) {
			address.setCountry(request.country());
		}
	}

	private static String optional(String current, String requested) {
		if (requested == null) {
			return current;
		}
		return blankToNull(requested);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

}
