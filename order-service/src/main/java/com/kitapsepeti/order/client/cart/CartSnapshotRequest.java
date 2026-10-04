package com.kitapsepeti.order.client.cart;

import java.util.Objects;
import java.util.UUID;

/** Cart {@code CartSnapshotRequest}. */
public record CartSnapshotRequest(UUID userId) {

	public CartSnapshotRequest {
		Objects.requireNonNull(userId, "userId");
	}

	@Override
	public String toString() {
		return "CartSnapshotRequest[redacted]";
	}

}
