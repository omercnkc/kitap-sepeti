package com.kitapsepeti.payment.config;

import java.time.Duration;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
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
		@NotNull @Valid @DefaultValue Mock mock, @NotNull @Valid @DefaultValue Webhook webhook) {

	/**
	 * @param failCents bu kuruşla biten tutarlar mock'ta reddedilir ({@link MockOutcomeRule})
	 * @param webhookSecret mock webhook imza anahtarı; biçimi Bean Validation ile değil {@link MockWebhookSigner}'da
	 * denetlenir (Bean Validation hatası reddedilen değeri yazar)
	 */
	public record Mock(@Min(MockOutcomeRule.MIN_FAIL_CENTS) @Max(MockOutcomeRule.MAX_FAIL_CENTS)
			@DefaultValue("99") int failCents, String webhookSecret) {

		@Override
		public String toString() {
			return "Mock[failCents=" + failCents + ", webhookSecret=" + ((webhookSecret == null) ? "null" : "***")
					+ "]";
		}

	}

	/**
	 * @param tolerance imza zaman damgasının saatten en fazla bu kadar geride ya da ileride olabileceği süre
	 * @param maxBodyBytes webhook gövdesinin üst sınırı; aşılırsa 413
	 */
	public record Webhook(@NotNull @DurationMin(seconds = 1) @DefaultValue("5m") Duration tolerance,
			@Min(1) @DefaultValue("65536") int maxBodyBytes) {
	}

}
