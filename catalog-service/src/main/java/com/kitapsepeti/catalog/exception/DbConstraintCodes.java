package com.kitapsepeti.catalog.exception;

import java.util.Map;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.common.error.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;

/**
 * Katalog DB kısıt ihlallerini {@link ErrorCode}'a çevirir (tek eşleme noktası). Kısıt adı/türü/MySQL kodu
 * {@link DbConstraints} ile okunur; mesaj kullanılmaz (çakışan slug/ISBN içerebilir). JPA yolunda UNIQUE ihlali
 * DuplicateKeyException olarak gelmez; tür Hibernate'ten okunur.
 */
public final class DbConstraintCodes {

	private static final Map<String, ErrorCode> UNIQUE_CODES = Map.of(
			"uk_authors_slug", CatalogErrorCode.SLUG_ALREADY_EXISTS,
			"uk_categories_slug", CatalogErrorCode.SLUG_ALREADY_EXISTS,
			"uk_books_isbn", CatalogErrorCode.ISBN_ALREADY_EXISTS);

	private DbConstraintCodes() {
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
		ConstraintViolationException violation = DbConstraints.find(ex);
		if (violation == null) {
			return new Violation(CommonErrorCode.CONFLICT, null, null);
		}
		String constraint = DbConstraints.normalize(violation.getConstraintName());
		ConstraintKind kind = violation.getKind();
		ErrorCode code = switch (kind) {
			case UNIQUE -> (constraint == null) ? CommonErrorCode.CONFLICT
					: UNIQUE_CODES.getOrDefault(constraint, CommonErrorCode.CONFLICT);
			case FOREIGN_KEY -> DbConstraints.isRowReferenced(violation) ? CatalogErrorCode.RESOURCE_IN_USE
					: CommonErrorCode.CONFLICT;
			default -> CommonErrorCode.CONFLICT;
		};
		return new Violation(code, constraint, kind);
	}

}
