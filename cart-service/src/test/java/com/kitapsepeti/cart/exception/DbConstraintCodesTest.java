package com.kitapsepeti.cart.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import com.kitapsepeti.common.error.CommonErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/** Spring context'siz: Hibernate istisnası elle kurulur, Spring'in sarmaladığı biçimde verilir. */
class DbConstraintCodesTest {

	@ParameterizedTest
	@ValueSource(strings = { "uk_carts_active_user", "carts.uk_carts_active_user", "UK_CARTS_ACTIVE_USER",
			"uk_cart_items_cart_book", "cart_items.uk_cart_items_cart_book" })
	void knownUniqueConstraintsMapToConflictWithNormalizedName(String constraintName) {
		DbConstraintCodes.Violation violation = DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, constraintName, 1062));

		assertThat(violation.code()).isEqualTo(CommonErrorCode.CONFLICT);
		assertThat(violation.constraint()).startsWith("uk_cart").doesNotContain(".");
		assertThat(violation.logNote()).isEqualTo("constraint=" + violation.constraint() + ", kind=UNIQUE");
	}

	@Test
	void otherConstraintsMapToConflict() {
		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, "carts.uk_bilinmeyen", 1062)).code())
			.isEqualTo(CommonErrorCode.CONFLICT);
		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.FOREIGN_KEY, "fk_cart_items_cart", 1452)).code())
			.isEqualTo(CommonErrorCode.CONFLICT);
		assertThat(DbConstraintCodes.classify(wrapped(ConstraintKind.FOREIGN_KEY, "fk_cart_items_cart", 1451)).code())
			.isEqualTo(CommonErrorCode.CONFLICT);

		DbConstraintCodes.Violation check = DbConstraintCodes.classify(wrapped(ConstraintKind.CHECK, "ck_cart_items_quantity", 3819));
		assertThat(check.code()).isEqualTo(CommonErrorCode.CONFLICT);
		assertThat(check.logNote()).isEqualTo("constraint=ck_cart_items_quantity, kind=CHECK");
	}

	@Test
	void unnamedConstraintMapsToConflictWithoutLogNote() {
		DbConstraintCodes.Violation unique = DbConstraintCodes.classify(wrapped(ConstraintKind.UNIQUE, null, 1062));
		assertThat(unique.code()).isEqualTo(CommonErrorCode.CONFLICT);
		assertThat(unique.logNote()).isNull();

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
