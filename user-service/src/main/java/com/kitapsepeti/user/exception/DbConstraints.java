package com.kitapsepeti.user.exception;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;

/**
 * DB kısıt ihlallerinden constraint adını okur. Exception mesajı kullanıcı verisi içerebildiği
 * için (örn. çakışan e-posta) mesaj yerine yalnızca Hibernate'in çıkardığı ad kullanılır.
 */
public final class DbConstraints {

	private DbConstraints() {
	}

	/** Zincirdeki Hibernate {@link ConstraintViolationException}'dan constraint adı; bulunamazsa null. */
	public static String nameOf(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

	/** MySQL adı "tablo.constraint" biçiminde de verebildiği için iki biçim de kabul edilir. */
	public static boolean isViolated(Throwable ex, String constraint) {
		String name = nameOf(ex);
		if (name == null) {
			return false;
		}
		String normalized = name.toLowerCase(Locale.ROOT);
		String expected = constraint.toLowerCase(Locale.ROOT);
		return normalized.equals(expected) || normalized.endsWith("." + expected);
	}

}
