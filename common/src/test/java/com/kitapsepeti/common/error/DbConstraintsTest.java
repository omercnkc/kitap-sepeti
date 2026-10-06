package com.kitapsepeti.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Spring context'siz: Hibernate istisnası elle kurulur, Spring'in sarmaladığı biçimde verilir. */
class DbConstraintsTest {

	@Test
	void findsHibernateViolationInCauseChain() {
		DataIntegrityViolationException ex = wrapped(ConstraintKind.UNIQUE, "users.uk_users_email", 1062);

		assertThat(DbConstraints.find(ex)).isSameAs(ex.getCause());
		assertThat(DbConstraints.nameOf(ex)).isEqualTo("users.uk_users_email");
	}

	@Test
	void missingHibernateCauseYieldsNull() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("no hibernate cause");

		assertThat(DbConstraints.find(ex)).isNull();
		assertThat(DbConstraints.nameOf(ex)).isNull();
		assertThat(DbConstraints.isViolated(ex, "uk_users_email")).isFalse();
	}

	@Test
	void normalizeStripsTablePrefixAndCase() {
		assertThat(DbConstraints.normalize("Books.UK_BOOKS_ISBN")).isEqualTo("uk_books_isbn");
		assertThat(DbConstraints.normalize("uk_books_isbn")).isEqualTo("uk_books_isbn");
		assertThat(DbConstraints.normalize(null)).isNull();
	}

	@Test
	void isViolatedIgnoresTablePrefixAndCase() {
		assertThat(DbConstraints.isViolated(wrapped(ConstraintKind.UNIQUE, "users.UK_USERS_EMAIL", 1062),
				"uk_users_email")).isTrue();
		assertThat(DbConstraints.isViolated(wrapped(ConstraintKind.UNIQUE, "uk_users_email", 1062),
				"UK_USERS_EMAIL")).isTrue();
		assertThat(DbConstraints.isViolated(wrapped(ConstraintKind.UNIQUE, "users.uk_users_phone", 1062),
				"uk_users_email")).isFalse();
		assertThat(DbConstraints.isViolated(wrapped(ConstraintKind.UNIQUE, null, 1062), "uk_users_email")).isFalse();
	}

	@Test
	void rowReferencedOnlyForForeignKey1451() {
		assertThat(DbConstraints.isRowReferenced(hibernate(ConstraintKind.FOREIGN_KEY, "fk_book_authors_author", 1451)))
			.isTrue();
		assertThat(DbConstraints.isRowReferenced(hibernate(ConstraintKind.FOREIGN_KEY, "fk_book_authors_author", 1452)))
			.isFalse();
		assertThat(DbConstraints.isRowReferenced(hibernate(ConstraintKind.UNIQUE, "uk_x", 1451))).isFalse();
	}

	private static DataIntegrityViolationException wrapped(ConstraintKind kind, String constraintName, int errorCode) {
		return new DataIntegrityViolationException("could not execute statement",
				hibernate(kind, constraintName, errorCode));
	}

	private static ConstraintViolationException hibernate(ConstraintKind kind, String constraintName, int errorCode) {
		SQLException sqlException = new SQLException("db message", "23000", errorCode);
		return new ConstraintViolationException("could not execute statement", sqlException,
				"insert into t values (?)", kind, constraintName);
	}

}
