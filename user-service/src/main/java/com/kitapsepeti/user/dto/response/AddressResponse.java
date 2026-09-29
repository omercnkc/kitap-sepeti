package com.kitapsepeti.user.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(UUID id, String label, String recipientName, String phone, String line1, String line2,
		String district, String city, String postalCode, String country, boolean isDefault, Instant createdAt) {

}
