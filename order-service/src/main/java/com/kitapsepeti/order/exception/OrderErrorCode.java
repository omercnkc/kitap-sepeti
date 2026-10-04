package com.kitapsepeti.order.exception;

import com.kitapsepeti.common.error.CommonErrorCode;
import com.kitapsepeti.common.error.ErrorCode;
import com.kitapsepeti.order.entity.OrderRuleViolation;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * order-service'e özgü hata kodları; ortak kodlar {@link CommonErrorCode}'da. Kod adı ProblemDetail yanıtında
 * {@code code} alanı olarak yazılır. Stack trace yalnızca {@link Level#ERROR} seviyesindeki kodlarda loglanır.
 * Sipariş yazıldıktan sonra dönen hatalarda yanıtta ayrıca {@code orderId} bulunur ({@link OrderProblemException}).
 */
public enum OrderErrorCode implements ErrorCode {

	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, Level.INFO, "Order was not found."),
	ORDER_PENDING_EXISTS(HttpStatus.CONFLICT, Level.INFO, "There is already a pending order; wait for it to complete."),
	CART_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "Cart is empty."),
	CART_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Level.WARN, "Cart is temporarily unavailable; retry later."),
	CATALOG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Level.WARN, "Catalog is temporarily unavailable; retry later."),
	BOOK_NOT_AVAILABLE(HttpStatus.CONFLICT, Level.INFO, "A book in the cart is not available for sale."),
	INSUFFICIENT_STOCK(HttpStatus.CONFLICT, Level.INFO, "There is not enough stock for a book in the cart."),
	MIXED_CURRENCY(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "Books in the cart have different currencies."),
	ORDER_TOTAL_ZERO(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "Order total must be greater than zero."),
	ORDER_TOTAL_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "Order total exceeds the maximum amount."),
	EMPTY_ORDER(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "Order has no items."),
	DUPLICATE_BOOK(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "A book appears more than once in the order."),
	INVALID_QUANTITY(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "An item quantity is out of the allowed range."),
	INVALID_PRICE(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "A book price cannot be used for an order."),
	INVALID_CURRENCY(HttpStatus.UNPROCESSABLE_CONTENT, Level.INFO, "A book currency cannot be used for an order."),
	PAYMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Level.WARN, "Payment is temporarily unavailable; retry later.");

	private final HttpStatus status;

	private final Level logLevel;

	private final String defaultDetail;

	OrderErrorCode(HttpStatus status, Level logLevel, String defaultDetail) {
		this.status = status;
		this.logLevel = logLevel;
		this.defaultDetail = defaultDetail;
	}

	/** {@link com.kitapsepeti.order.entity.Order#place} kural ihlalinin API kodu (aynı ad, 422). */
	public static OrderErrorCode of(OrderRuleViolation.Code code) {
		return switch (code) {
			case EMPTY_ORDER -> EMPTY_ORDER;
			case DUPLICATE_BOOK -> DUPLICATE_BOOK;
			case INVALID_QUANTITY -> INVALID_QUANTITY;
			case INVALID_PRICE -> INVALID_PRICE;
			case INVALID_CURRENCY -> INVALID_CURRENCY;
			case ORDER_TOTAL_ZERO -> ORDER_TOTAL_ZERO;
		};
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public Level logLevel() {
		return this.logLevel;
	}

	@Override
	public String defaultDetail() {
		return this.defaultDetail;
	}

}
