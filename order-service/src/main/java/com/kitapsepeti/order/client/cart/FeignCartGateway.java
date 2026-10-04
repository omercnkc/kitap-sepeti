package com.kitapsepeti.order.client.cart;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.order.client.CallOutcome;
import com.kitapsepeti.order.client.Downstream;
import com.kitapsepeti.order.client.InvalidResponseException;
import com.kitapsepeti.order.client.RemoteCalls;
import com.kitapsepeti.order.gateway.CartGateway;
import com.kitapsepeti.order.gateway.CartSnapshotResult;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.gateway.Unavailable;
import org.springframework.stereotype.Component;

/** Cart snapshot: 200 tek başarı yanıtı; her hata (4xx dahil) {@link Unavailable} (okuma, yan etki yok). */
@Component
public class FeignCartGateway implements CartGateway {

	/** Cart {@code CartSnapshotItem.quantity} aralığı. */
	public static final int MAX_QUANTITY = 99;

	private final CartClient client;

	private final RemoteCalls calls;

	public FeignCartGateway(CartClient client, RemoteCalls calls) {
		this.client = client;
		this.calls = calls;
	}

	@Override
	public CartSnapshotResult snapshot(UUID userId) {
		Objects.requireNonNull(userId, "userId");
		return this.calls.execute(Downstream.CART, "snapshot",
				() -> this.client.snapshot(new CartSnapshotRequest(userId)), FeignCartGateway::validate,
				FeignCartGateway::toResult);
	}

	private static void validate(CartSnapshotResponse body) {
		if (body.cartId() == null && !body.items().isEmpty()) {
			throw new InvalidResponseException("Cart snapshot has items without a cart");
		}
		Set<UUID> seen = new HashSet<>();
		for (CartSnapshotResponse.Item item : body.items()) {
			if (item.quantity() < 1 || item.quantity() > MAX_QUANTITY) {
				throw new InvalidResponseException("Cart snapshot item quantity is out of range");
			}
			if (!seen.add(item.bookId())) {
				throw new InvalidResponseException("Cart snapshot repeats a book");
			}
		}
	}

	private static CartSnapshotResult toResult(CallOutcome<CartSnapshotResponse> outcome) {
		if (!(outcome instanceof CallOutcome.Success<CartSnapshotResponse> success)) {
			return new Unavailable();
		}
		CartSnapshotResponse body = success.body();
		if (body.cartId() == null || body.items().isEmpty()) {
			return new CartSnapshotResult.Empty();
		}
		List<StockLine> lines = body.items()
			.stream()
			.map(item -> new StockLine(item.bookId(), item.quantity()))
			.toList();
		return new CartSnapshotResult.Snapshot(body.cartId(), lines);
	}

}
