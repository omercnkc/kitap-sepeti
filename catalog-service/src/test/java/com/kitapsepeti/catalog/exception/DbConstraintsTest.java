package com.kitapsepeti.catalog.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Spring context'siz: Hibernate istisnası elle kurulur, Spring'in sarmaladığı biçimde verilir. */
class DbConstraintsTest {

	@Test
	void foreignKeyRowIsReferencedMapsToResourceInUse() {
		DbConstraints.Violation violation = DbConstraints.classify(wrapped(ConstraintKind.FOREIGN_KEY,
				"fk_books_publisher", 1451));

		assertThat(violation.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE);
		assertThat(violation.logNote()).isEqualTo("constraint=fk_books_publisher, kind=FOREIGN_KEY");
	}

	@Test
	void foreignKeyMissingParentMapsToConflict() {
		DbConstraints.Violation violation = DbConstraints.classify(wrapped(ConstraintKind.FOREIGN_KEY,
				"fk_books_publisher", 1452));

		assertThat(violation.code()).isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	void tablePrefixedUniqueNamesMapToSpecificCodes() {
		assertThat(DbConstraints.classify(wrapped(ConstraintKind.UNIQUE, "publishers.uk_publishers_slug", 1062)).code())
			.isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);
		assertThat(DbConstraints.classify(wrapped(ConstraintKind.UNIQUE, "authors.UK_AUTHORS_SLUG", 1062)).code())
			.isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);
		assertThat(DbConstraints.classify(wrapped(ConstraintKind.UNIQUE, "categories.uk_categories_slug", 1062)).code())
			.isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);

		DbConstraints.Violation isbn = DbConstraints.classify(wrapped(ConstraintKind.UNIQUE, "books.uk_books_isbn", 1062));
		assertThat(isbn.code()).isEqualTo(ErrorCode.ISBN_ALREADY_EXISTS);
		assertThat(isbn.constraint()).isEqualTo("uk_books_isbn");

		assertThat(DbConstraints.classify(wrapped(ConstraintKind.UNIQUE, "books.uk_bilinmeyen", 1062)).code())
			.isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	void unnamedConstraintMapsToConflictWithoutLogNote() {
		DbConstraints.Violation unique = DbConstraints.classify(wrapped(ConstraintKind.UNIQUE, null, 1062));
		assertThat(unique.code()).isEqualTo(ErrorCode.CONFLICT);
		assertThat(unique.logNote()).isNull();

		assertThat(DbConstraints.classify(wrapped(ConstraintKind.FOREIGN_KEY, null, 1452)).code())
			.isEqualTo(ErrorCode.CONFLICT);
		assertThat(DbConstraints.classify(new DataIntegrityViolationException("no hibernate cause")).code())
			.isEqualTo(ErrorCode.CONFLICT);
	}

	private static DataIntegrityViolationException wrapped(ConstraintKind kind, String constraintName, int errorCode) {
		SQLException sqlException = new SQLException("db message", "23000", errorCode);
		ConstraintViolationException hibernate = new ConstraintViolationException("could not execute statement",
				sqlException, "insert into t values (?)", kind, constraintName);
		return new DataIntegrityViolationException("could not execute statement", hibernate);
	}

}
