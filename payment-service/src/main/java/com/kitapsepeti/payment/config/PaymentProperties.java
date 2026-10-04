package com.kitapsepeti.payment.config;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.MockOutcomeRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Ödeme ayarları ({@code app.payment.*}); geçersiz değerle uygulama açılmaz.
 *
 * @param provider kullanılacak sağlayıcı; v1'de yalnızca {@code mock} desteklenir ({@link PaymentConfig})
 */
@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(@NotNull @DefaultValue("mock") PaymentProviderType provider,
		@NotNull @Valid @DefaultValue Mock mock) {

	/** @param failCents bu kuruşla biten tutarlar mock'ta reddedilir ({@link MockOutcomeRule}) */
	public record Mock(@Min(MockOutcomeRule.MIN_FAIL_CENTS) @Max(MockOutcomeRule.MAX_FAIL_CENTS)
			@DefaultValue("99") int failCents) {
	}

}
