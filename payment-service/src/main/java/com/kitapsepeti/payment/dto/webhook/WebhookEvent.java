package com.kitapsepeti.payment.dto.webhook;

import java.math.BigDecimal;

import com.kitapsepeti.payment.entity.ProviderEventType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mock sağlayıcının webhook gövdesi. Bilinmeyen alanlar yok sayılır. Tüm alanlar JSON metnidir (tutar da: sayı
 * gelirse gövde okunamaz sayılır).
 *
 * @param eventId sağlayıcının olay kimliği; tekrar teslimde aynıdır
 * @param providerPaymentId ödeme oluşturulurken sağlayıcının döndürdüğü referans
 * @param type {@code payment.succeeded} ya da {@code payment.failed}
 * @param amount ödemenin tutarı, tam 2 ondalık ({@code "149.99"})
 * @param failureCode yalnızca {@code payment.failed}'da ve zorunlu
 */
public record WebhookEvent(
		@NotNull @Size(min = 1, max = 128) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String eventId,
		@NotBlank @Size(max = 128) String providerPaymentId,
		@NotNull @Pattern(regexp = "^payment\\.(succeeded|failed)$") String type,
		@NotNull @Pattern(regexp = "^(0|[1-9][0-9]{0,9})\\.[0-9]{2}$") String amount,
		@NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
		@Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$") String failureCode) {

	/** {@code failureCode} failed'da zorunlu, succeeded'da yok (null). Hata alanı {@code failureCodeMatchesType}. */
	@AssertTrue(message = "failureCode is required for payment.failed and not allowed for payment.succeeded")
	public boolean isFailureCodeMatchesType() {
		if (ProviderEventType.PAYMENT_FAILED.dbValue().equals(type)) {
			return failureCode != null;
		}
		return failureCode == null;
	}

	/** Yalnızca doğrulanmış olayda çağrılır. */
	public ProviderEventType eventType() {
		return ProviderEventType.PAYMENT_FAILED.dbValue().equals(type) ? ProviderEventType.PAYMENT_FAILED
				: ProviderEventType.PAYMENT_SUCCEEDED;
	}

	/** Yalnızca doğrulanmış olayda çağrılır. */
	public BigDecimal amountValue() {
		return new BigDecimal(amount);
	}

}
