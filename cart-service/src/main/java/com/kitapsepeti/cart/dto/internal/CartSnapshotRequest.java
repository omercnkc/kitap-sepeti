package com.kitapsepeti.cart.dto.internal;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Order'ın checkout'ta sepetini istediği kullanıcı; kullanıcı kimliği Order'ın doğruladığı token'dan gelir. */
public record CartSnapshotRequest(
		@Schema(description = "Sepeti istenen kullanıcının id'si (token'ın `sub`'ı).") @NotNull UUID userId) {
}
