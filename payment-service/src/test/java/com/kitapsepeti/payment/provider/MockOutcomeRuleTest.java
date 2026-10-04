package com.kitapsepeti.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MockOutcomeRuleTest {

	private final MockOutcomeRule defaultRule = new MockOutcomeRule(99);

	@ParameterizedTest
	@ValueSource(strings = { "10.99", "0.99", "1234567.99", "10.990" })
	void amountsEndingWithFailCentsAreDeclined(String amount) {
		assertThat(defaultRule.outcomeFor(new BigDecimal(amount)))
			.isEqualTo(MockOutcome.failed(MockOutcomeRule.CARD_DECLINED));
	}

	@ParameterizedTest
	@ValueSource(strings = { "10.00", "10.98", "10", "0.01", "99.00", "9.9" })
	void otherAmountsSucceed(String amount) {
		MockOutcome outcome = defaultRule.outcomeFor(new BigDecimal(amount));

		assertThat(outcome.isSucceeded()).isTrue();
		assertThat(outcome.failureCode()).isNull();
	}

	@Test
	void failCentsIsConfigurable() {
		MockOutcomeRule fifty = new MockOutcomeRule(50);
		MockOutcomeRule zero = new MockOutcomeRule(0);

		assertThat(fifty.outcomeFor(new BigDecimal("10.50")).isSucceeded()).isFalse();
		assertThat(fifty.outcomeFor(new BigDecimal("10.5")).isSucceeded()).isFalse();
		assertThat(fifty.outcomeFor(new BigDecimal("10.99")).isSucceeded()).isTrue();
		assertThat(zero.outcomeFor(new BigDecimal("10.00")).failureCode()).isEqualTo(MockOutcomeRule.CARD_DECLINED);
		assertThat(zero.outcomeFor(new BigDecimal("10")).isSucceeded()).isFalse();
		assertThat(zero.outcomeFor(new BigDecimal("10.01")).isSucceeded()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 100 })
	void rejectsFailCentsOutsideZeroToNinetyNine(int failCents) {
		assertThatThrownBy(() -> new MockOutcomeRule(failCents)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-10.99", "10.999" })
	void rejectsAmountsThatCannotBePayments(String amount) {
		assertThatThrownBy(() -> defaultRule.outcomeFor(new BigDecimal(amount)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void failedOutcomeRequiresCode() {
		assertThatThrownBy(() -> MockOutcome.failed(null)).isInstanceOf(NullPointerException.class);
		assertThat(MockOutcome.succeeded().failureCode()).isNull();
	}

}
