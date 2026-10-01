package com.kitapsepeti.catalog.exception;

import java.util.Locale;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;

/**
 * DB kısıt ihlallerini {@link ErrorCode}'a çevirir (tek eşleme noktası). Exception mesajı kullanıcı verisi
 * içerebildiği için (örn. çakışan slug/ISBN) mesaj yerine yalnızca Hibernate'in çıkardığı kısıt adı, türü ve
 * MySQL hata kodu kullanılır. JPA yolunda UNIQUE ihlali DuplicateKeyException olarak gelmez; tür Hibernate'ten okunur.
 */
public final class DbConstraints {

	/** ER_ROW_IS_REFERENCED_2: başka satırların başvurduğu üst kayıt silinemez/değiştirilemez. */
	private static final int MYSQL_ROW_IS_REFERENCED = 1451;

	private static final Map<String, ErrorCode> UNIQUE_CODES = Map.of(
			"uk_publishers_slug", ErrorCode.SLUG_ALREADY_EXISTS,
			"uk_authors_slug", ErrorCode.SLUG_ALREADY_EXISTS,
			"uk_categories_slug", ErrorCode.SLUG_ALREADY_EXISTS,
			"uk_books_isbn", ErrorCode.ISBN_ALREADY_EXISTS);

	private DbConstraints() {
	}

	/**
	 * Eşleme sonucu. {@code constraint} ve {@code kind} Hibernate istisnası bulunamazsa null'dır.
	 * {@link #logNote()} yalnızca kısıt adı ve türünü içerir; değer içermez.
	 */
	public record Violation(ErrorCode code, String constraint, ConstraintKind kind) {

		public String logNote() {
			return (constraint == null) ? null : "constraint=" + constraint + ", kind=" + kind;
		}

	}

	/**
	 * UNIQUE: bilinen slug/ISBN kısıtları özel koda, diğerleri CONFLICT. FOREIGN_KEY: 1451 (üst kayıt kullanımda)
	 * RESOURCE_IN_USE, 1452 (olmayan kayda başvuru) ve diğerleri CONFLICT. CHECK ve geri kalan her şey CONFLICT.
	 */
	public static Violation classify(Throwable ex) {
		ConstraintViolationException violation = find(ex);
		if (violation == null) {
			return new Violation(ErrorCode.CONFLICT, null, null);
		}
		String constraint = normalize(violation.getConstraintName());
		ConstraintKind kind = violation.getKind();
		ErrorCode code = switch (kind) {
			case UNIQUE -> (constraint == null) ? ErrorCode.CONFLICT
					: UNIQUE_CODES.getOrDefault(constraint, ErrorCode.CONFLICT);
			case FOREIGN_KEY -> (violation.getErrorCode() == MYSQL_ROW_IS_REFERENCED) ? ErrorCode.RESOURCE_IN_USE
					: ErrorCode.CONFLICT;
			default -> ErrorCode.CONFLICT;
		};
		return new Violation(code, constraint, kind);
	}

	/** MySQL UNIQUE ihlalinde adı "tablo.kısıt" biçiminde verir; tablo öneki atılır. */
	static String normalize(String name) {
		if (name == null) {
			return null;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.substring(lower.lastIndexOf('.') + 1);
	}

	private static ConstraintViolationException find(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation;
			}
		}
		return null;
	}

}
