package com.kitapsepeti.cart.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

class CartErrorCodeTest {

	/**
	 * Küme = tüm cart kodları ∪ tüm ortak kodlar (cart JWKS ile doğrular → AUTHENTICATION_UNAVAILABLE dahil).
	 * Enum'a eklenip listeye yazılmayan kod bu testi kırar.
	 */
	@Test
	void apiCodesListEveryReturnableCodeExactlyOnce() {
		Set<ErrorCode> returnable = new HashSet<>(EnumSet.allOf(CartErrorCode.class));
		returnable.addAll(EnumSet.allOf(CommonErrorCode.class));

		assertThat(CartErrorCode.API_CODES).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(returnable);
	}

	/** Sıra: ortak genel kodlar → cart kodları (enum sırası) → AUTHENTICATION_UNAVAILABLE → INTERNAL_ERROR. */
	@Test
	void apiCodesOrder() {
		List<ErrorCode> codes = CartErrorCode.API_CODES;
		int firstCartCode = codes.indexOf(CartErrorCode.values()[0]);

		assertThat(codes.subList(0, firstCartCode)).allMatch(CommonErrorCode.class::isInstance)
			.doesNotContain(CommonErrorCode.AUTHENTICATION_UNAVAILABLE, CommonErrorCode.INTERNAL_ERROR);
		assertThat(codes.subList(firstCartCode, firstCartCode + CartErrorCode.values().length))
			.containsExactly(CartErrorCode.values());
		assertThat(codes.subList(codes.size() - 2, codes.size()))
			.containsExactly(CommonErrorCode.AUTHENTICATION_UNAVAILABLE, CommonErrorCode.INTERNAL_ERROR);
	}

	@Test
	void statusAndLogLevel() {
		assertThat(CartErrorCode.CART_LINE_LIMIT_EXCEEDED.status()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(CartErrorCode.CART_QUANTITY_LIMIT_EXCEEDED.status()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(CartErrorCode.BOOK_NOT_AVAILABLE.status()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(CartErrorCode.CATALOG_UNAVAILABLE.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(CartErrorCode.CATALOG_UNAVAILABLE.logLevel()).isEqualTo(Level.WARN);
		assertThat(EnumSet.range(CartErrorCode.CART_LINE_LIMIT_EXCEEDED, CartErrorCode.BOOK_NOT_AVAILABLE))
			.allMatch(code -> code.logLevel() == Level.INFO);
	}

	@Test
	void limitExceptionsCarryTheirCodeAndLimit() {
		CartLimitExceededException lines = CartLimitExceededException.lines(50);
		CartLimitExceededException quantity = CartLimitExceededException.quantityPerItem(10);

		assertThat(lines.getErrorCode()).isEqualTo(CartErrorCode.CART_LINE_LIMIT_EXCEEDED);
		assertThat(lines.getLimit()).isEqualTo(50);
		assertThat(quantity.getErrorCode()).isEqualTo(CartErrorCode.CART_QUANTITY_LIMIT_EXCEEDED);
		assertThat(quantity.getLimit()).isEqualTo(10);
		assertThat(lines.getDetail()).isEqualTo(CartErrorCode.CART_LINE_LIMIT_EXCEEDED.defaultDetail());
	}

}
