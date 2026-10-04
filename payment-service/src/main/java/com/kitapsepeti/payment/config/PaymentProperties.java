package com.kitapsepeti.payment.config;

import java.time.Duration;

import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.provider.mock.MockWebhookSigner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
	 * @param delay referans yazıldıktan (commit) sonra mock webhook'un gönderilmesine kadar geçen süre
	 * @param webhookUrl mock webhook'un gönderileceği adres; yok ya da boşsa uygulamanın dinlediği port
	 * ({@code http://localhost:<port>/webhooks/mock})
	 */
	public record Mock(@Min(MockOutcomeRule.MIN_FAIL_CENTS) @Max(MockOutcomeRule.MAX_FAIL_CENTS)
			@DefaultValue("99") int failCents, String webhookSecret,
			@NotNull @DurationMin(millis = 0) @DefaultValue("500ms") Duration delay,
			@Pattern(regexp = "^$|^https?://\\S+$") String webhookUrl, @NotNull @Valid @DefaultValue Dispatch dispatch,
			@NotNull @Valid @DefaultValue Recovery recovery) {

		@Override
		public String toString() {
			return "Mock[failCents=" + failCents + ", webhookSecret=" + ((webhookSecret == null) ? "null" : "***")
					+ ", delay=" + delay + ", webhookUrl=" + webhookUrl + ", dispatch=" + dispatch + ", recovery="
					+ recovery + "]";
		}

	}

	/**
	 * @param enabled referans yazılınca webhook otomatik planlanır mı; kapalıyken yalnızca kurtarma görevi gönderir
	 * @param queueCapacity planlanmış ama henüz gönderilmemiş en fazla webhook sayısı; dolunca yenisi düşürülür
	 */
	public record Dispatch(@DefaultValue("true") boolean enabled, @Min(1) @DefaultValue("100") int queueCapacity) {
	}

	/**
	 * @param enabled kurtarma görevi çalışır mı
	 * @param interval iki tur arası bekleme (önceki tur bittikten sonra)
	 * @param minAge bundan daha yeni ödemeler atlanır (normal gönderim henüz sürüyor olabilir)
	 * @param batchSize bir turda en fazla bu kadar ödeme
	 */
	public record Recovery(@DefaultValue("true") boolean enabled,
			@NotNull @DurationMin(seconds = 1) @DefaultValue("30s") Duration interval,
			@NotNull @DurationMin(seconds = 1) @DefaultValue("10s") Duration minAge,
			@Min(1) @Max(1000) @DefaultValue("50") int batchSize) {
	}

	/**
	 * @param tolerance imza zaman damgasının saatten en fazla bu kadar geride ya da ileride olabileceği süre
	 * @param maxBodyBytes webhook gövdesinin üst sınırı; aşılırsa 413
	 */
	public record Webhook(@NotNull @DurationMin(seconds = 1) @DefaultValue("5m") Duration tolerance,
			@Min(1) @DefaultValue("65536") int maxBodyBytes) {
	}

}
