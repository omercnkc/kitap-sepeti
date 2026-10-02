package com.kitapsepeti.cart.dto.internal;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Order'ın checkout'ta sepetini istediği kullanıcı; kullanıcı kimliği Order'ın doğruladığı token'dan gelir. */
public record CartSnapshotRequest(@NotNull UUID userId) {
}
