package com.kitapsepeti.catalog.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import com.kitapsepeti.common.error.CommonErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Spring context'siz: Hibernate istisnası elle kurulur, Spring'in sarmaladığı biçimde verilir. */
class DbConstraintCodesTest {

	@Test
	void foreignKeyRowIsReferencedMapsToResourceInUse() {
		DbConstraintCodes.Violation violation = DbConstraintCodes.classify(wrapped(ConstraintKind.FOREIGN_KEY,
				"fk_book_authors_author", 1451));

		assertThat(violation.code()).isEqualTo(CatalogErrorCode.RESOURCE_IN_USE);
		assertThat(violation.logNote()).isEqualTo("constraint=fk_book_authors_author, kind=FOREIGN_KEY");
	}

	@Test
	void foreignKeyMissingParentMapsToConflict() {
		DbConstraintCodes.Violation violation = DbConstraintCodes.classify(wrapped(ConstraintKind.FOREIGN_KEY,
				"fk_book_authors_author", 1452));

		assertThat(violation.code()).isEqualTo(CommonErrorCode.CONFLICT);
	}

	@Test
	void tablePrefixedUniqueNamesMapToSpecificCodes() {
		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, "authors.uk_authors_slug", 1062)).code())
			.isEqualTo(CatalogErrorCode.SLUG_ALREADY_EXISTS);
		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, "authors.UK_AUTHORS_SLUG", 1062)).code())
			.isEqualTo(CatalogErrorCode.SLUG_ALREADY_EXISTS);
		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, "categories.uk_categories_slug", 1062)).code())
			.isEqualTo(CatalogErrorCode.SLUG_ALREADY_EXISTS);

		DbConstraintCodes.Violation isbn = DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, "books.uk_books_isbn", 1062));
		assertThat(isbn.code()).isEqualTo(CatalogErrorCode.ISBN_ALREADY_EXISTS);
		assertThat(isbn.constraint()).isEqualTo("uk_books_isbn");

		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, "books.uk_bilinmeyen", 1062)).code())
			.isEqualTo(CommonErrorCode.CONFLICT);
	}

	@Test
	void unnamedConstraintMapsToConflictWithoutLogNote() {
		DbConstraintCodes.Violation unique = DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, null, 1062));
		assertThat(unique.code()).isEqualTo(CommonErrorCode.CONFLICT);
		assertThat(unique.logNote()).isNull();

		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.FOREIGN_KEY, null, 1452)).code())
			.isEqualTo(CommonErrorCode.CONFLICT);
		assertThat(DbConstraintCodes.classify(new DataIntegrityViolationException("no hibernate cause")).code())
			.isEqualTo(CommonErrorCode.CONFLICT);
	}

	private static DataIntegrityViolationException wrapped(ConstraintKind kind, String constraintName, int errorCode) {
		SQLException sqlException = new SQLException("db message", "23000", errorCode);
		ConstraintViolationException hibernate = new ConstraintViolationException("could not execute statement",
				sqlException, "insert into t values (?)", kind, constraintName);
		return new DataIntegrityViolationException("could not execute statement", hibernate);
	}

}
