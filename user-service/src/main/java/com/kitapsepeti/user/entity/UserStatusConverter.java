package com.kitapsepeti.user.entity;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link UserStatus} değerini DB'ye küçük harfle yazar, okurken enum'a çevirir.
 * {@code @Enumerated} kullanılmaz; çünkü {@code ck_users_status} kısıtı yalnızca
 * {@code 'active'} ve {@code 'suspended'} kabul eder, büyük harfi reddeder.
 */
@Converter
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

	@Override
	public String convertToDatabaseColumn(UserStatus status) {
		return status == null ? null : status.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public UserStatus convertToEntityAttribute(String value) {
		return value == null ? null : UserStatus.valueOf(value.toUpperCase(Locale.ROOT));
	}

}
