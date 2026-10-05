package com.kitapsepeti.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.cart.client.CatalogBook;
import com.kitapsepeti.cart.client.CatalogGateway;
import com.kitapsepeti.cart.dto.request.AddCartItemRequest;
import com.kitapsepeti.cart.dto.response.CartResponse;
import com.kitapsepeti.cart.exception.BookNotAvailableException;
import com.kitapsepeti.cart.exception.CatalogUnavailableException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

/** {@link CartService}'in akış kuralları: Catalog transaction'dan önce, yalnızca ilk sepet yarışı bir kez yeniden denenir. */
class CartServiceTest {

	private final CatalogGateway catalog = mock(CatalogGateway.class);

	private final CartTransactions transactions = mock(CartTransactions.class);

	private final CartViewAssembler assembler = mock(CartViewAssembler.class);

	private final CartService service = new CartService(catalog, transactions, assembler);

	private final UUID userId = UUID.randomUUID();

	private final CatalogBook book = new CatalogBook(UUID.randomUUID(), "Kitap", new BigDecimal("10.00"), "TRY", null,
			true);

	private final AddCartItemRequest request = new AddCartItemRequest(book.id(), 2);

	@Test
	void activeCartViolationIsRetriedOnceInNewTransaction() {
		CartContents contents = new CartContents(List.of());
		when(catalog.requireAvailableBook(book.id())).thenReturn(book);
		when(transactions.addItem(userId, book, 2)).thenThrow(violation("uk_carts_active_user")).thenReturn(contents);
		when(assembler.assemble(contents)).thenReturn(CartResponse.empty());

		assertThat(service.addItem(userId, request)).isSameAs(CartResponse.empty());
		verify(transactions, times(2)).addItem(userId, book, 2);
		verify(catalog, times(1)).requireAvailableBook(book.id());
	}

	@Test
	void secondActiveCartViolationPropagates() {
		DataIntegrityViolationException second = violation("uk_carts_active_user");
		when(catalog.requireAvailableBook(book.id())).thenReturn(book);
		when(transactions.addItem(userId, book, 2)).thenThrow(violation("uk_carts_active_user")).thenThrow(second);

		assertThatThrownBy(() -> service.addItem(userId, request)).isSameAs(second);
		verify(transactions, times(2)).addItem(userId, book, 2);
		verifyNoInteractions(assembler);
	}

	@Test
	void firstCartDeadlockIsRetriedOnceInNewTransaction() {
		CartContents contents = new CartContents(List.of());
		when(catalog.requireAvailableBook(book.id())).thenReturn(book);
		when(transactions.addItem(userId, book, 2)).thenThrow(lockFailure(1213)).thenReturn(contents);
		when(assembler.assemble(contents)).thenReturn(CartResponse.empty());

		assertThat(service.addItem(userId, request)).isSameAs(CartResponse.empty());
		verify(transactions, times(2)).addItem(userId, book, 2);
		verify(catalog, times(1)).requireAvailableBook(book.id());
	}

	@Test
	void secondDeadlockPropagates() {
		CannotAcquireLockException second = lockFailure(1213);
		when(catalog.requireAvailableBook(book.id())).thenReturn(book);
		when(transactions.addItem(userId, book, 2)).thenThrow(violation("uk_carts_active_user")).thenThrow(second);

		assertThatThrownBy(() -> service.addItem(userId, request)).isSameAs(second);
		verify(transactions, times(2)).addItem(userId, book, 2);
		verifyNoInteractions(assembler);
	}

	@Test
	void lockWaitTimeoutIsNotRetried() {
		CannotAcquireLockException timeout = lockFailure(1205);
		when(catalog.requireAvailableBook(book.id())).thenReturn(book);
		when(transactions.addItem(userId, book, 2)).thenThrow(timeout);

		assertThatThrownBy(() -> service.addItem(userId, request)).isSameAs(timeout);
		verify(transactions, times(1)).addItem(userId, book, 2);
	}

	@Test
	void otherConstraintViolationsAreNotRetried() {
		DataIntegrityViolationException other = violation("uk_cart_items_cart_book");
		when(catalog.requireAvailableBook(book.id())).thenReturn(book);
		when(transactions.addItem(userId, book, 2)).thenThrow(other);

		assertThatThrownBy(() -> service.addItem(userId, request)).isSameAs(other);
		verify(transactions, times(1)).addItem(userId, book, 2);
	}

	@Test
	void catalogRejectionOrOutageNeverOpensTransaction() {
		when(catalog.requireAvailableBook(book.id())).thenThrow(new BookNotAvailableException())
			.thenThrow(new CatalogUnavailableException(new IOException("x")));

		assertThatThrownBy(() -> service.addItem(userId, request)).isInstanceOf(BookNotAvailableException.class);
		assertThatThrownBy(() -> service.addItem(userId, request)).isInstanceOf(CatalogUnavailableException.class);
		verifyNoInteractions(transactions, assembler);
	}

	@Test
	void missingQuantityDefaultsToOne() {
		assertThat(new AddCartItemRequest(book.id(), null).quantity()).isEqualTo(1);
	}

	private static DataIntegrityViolationException violation(String constraint) {
		ConstraintViolationException hibernate = new ConstraintViolationException("could not execute statement",
				new SQLException("db message", "23000", 1062), "insert into t values (?)", ConstraintKind.UNIQUE,
				constraint);
		return new DataIntegrityViolationException("could not execute statement", hibernate);
	}

	private static CannotAcquireLockException lockFailure(int mysqlErrorCode) {
		return new CannotAcquireLockException("could not execute statement",
				new SQLException("db message", "40001", mysqlErrorCode));
	}

}
