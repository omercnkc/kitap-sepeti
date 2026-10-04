package com.kitapsepeti.payment.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Mock sonuç kuralı (saf): tutarın kuruş kısmı {@code failCents} ise {@value #CARD_DECLINED} ile failed, değilse
 * succeeded. Varsayılan 99: 10.99 ve 0.99 reddedilir, 10.00 ve 10.98 başarılı.
 */
public class MockOutcomeRule {

	public static final String CARD_DECLINED = "CARD_DECLINED";

	public static final int MIN_FAIL_CENTS = 0;

	public static final int MAX_FAIL_CENTS = 99;

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final int failCents;

	public MockOutcomeRule(int failCents) {
		if (failCents < MIN_FAIL_CENTS || failCents > MAX_FAIL_CENTS) {
			throw new IllegalArgumentException(
					"failCents must be between " + MIN_FAIL_CENTS + " and " + MAX_FAIL_CENTS + ": " + failCents);
		}
		this.failCents = failCents;
	}

	/**
	 * @param amount pozitif, en fazla 2 ondalık ({@code payments.amount} ile aynı)
	 * @throws IllegalArgumentException tutar pozitif değilse ya da 2'den fazla ondalık içeriyorsa
	 */
	public MockOutcome outcomeFor(BigDecimal amount) {
		return centsOf(amount) == failCents ? MockOutcome.failed(CARD_DECLINED) : MockOutcome.succeeded();
	}

	private static int centsOf(BigDecimal amount) {
		Objects.requireNonNull(amount, "amount");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
		try {
			return amount.setScale(2, RoundingMode.UNNECESSARY).remainder(BigDecimal.ONE).multiply(HUNDRED)
				.intValueExact();
		}
		catch (ArithmeticException ex) {
			throw new IllegalArgumentException("Amount must have at most 2 decimal places", ex);
		}
	}

}
