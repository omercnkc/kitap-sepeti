package com.kitapsepeti.payment.provider;

import java.util.Objects;

/**
 * Mock sağlayıcının bir ödeme için vereceği sonuç.
 *
 * @param failureCode başarılıysa null
 */
public record MockOutcome(String failureCode) {

	private static final MockOutcome SUCCEEDED = new MockOutcome(null);

	public static MockOutcome succeeded() {
		return SUCCEEDED;
	}

	public static MockOutcome failed(String failureCode) {
		return new MockOutcome(Objects.requireNonNull(failureCode, "failureCode"));
	}

	public boolean isSucceeded() {
		return failureCode == null;
	}

}
