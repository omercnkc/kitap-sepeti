package com.kitapsepeti.payment.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Siparişin ödemesi ({@code payments} tablosu); sipariş başına tek ödeme ({@code uk_payments_order}).
 * <p>
 * Setter yok: durum, hata kodu ve sağlayıcı referansı yalnızca domain metotlarıyla değişir; metotlar DB CHECK'lerinin
 * (tutar, para birimi, "failed ⇔ failure_code") Java karşılığını uygular, bu yüzden JPA yolundan CHECK ihlali çıkmaz.
 * Akış: {@link #initiate} → kaydet → sağlayıcıda ödeme oluştur → {@link #attachProviderReference} → sonuç
 * ({@link #succeed} / {@link #fail}).
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	public static final int PROVIDER_REFERENCE_MAX_LENGTH = 128;

	/** DECIMAL(12,2) üst sınırı. */
	public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

	private static final int AMOUNT_SCALE = 2;

	private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

	private static final Pattern FAILURE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

	/** Uygulama tarafında üretilen, zamana göre sıralı UUID v7; DB'de BINARY(16). */
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** order-service'teki sipariş; servisler arası olduğu için FK yok. */
	@Column(name = "order_id", nullable = false, updatable = false)
	private UUID orderId;

	/** user-service'teki kullanıcı. */
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Convert(converter = PaymentProviderTypeConverter.class)
	@Column(name = "provider", nullable = false, updatable = false, length = 16)
	private PaymentProviderType providerType;

	/** Sağlayıcıdaki ödeme referansı; {@link #attachProviderReference} ile bir kez yazılır, öncesinde null. */
	@Column(name = "provider_payment_id", length = PROVIDER_REFERENCE_MAX_LENGTH)
	private String providerPaymentId;

	/** Her zaman scale 2 ve pozitif ({@code ck_payments_amount}). */
	@Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	/** ISO 4217, büyük harf ({@code ck_payments_currency}). */
	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Convert(converter = PaymentStatusConverter.class)
	@Column(name = "status", nullable = false, length = 16)
	private PaymentStatus status = PaymentStatus.INITIATED;

	/** Yalnızca {@link PaymentStatus#FAILED} ödemede dolu ({@code ck_payments_failure}). */
	@Column(name = "failure_code", length = 64)
	private String failureCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Durumu ya da referansı değiştiren her metot verilen saatle yeniler; değişiklik yoksa dokunulmaz. */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Yeni, {@code initiated} durumunda ödeme (sağlayıcı referansı henüz yok).
	 *
	 * @param amount pozitif, en fazla 2 ondalık (10.001 yuvarlanmaz, reddedilir), {@link #MAX_AMOUNT}'u aşamaz
	 * @param currency 3 büyük harf (ör. {@code TRY})
	 * @throws IllegalArgumentException değerlerden biri geçersizse
	 */
	public static Payment initiate(UUID orderId, UUID userId, BigDecimal amount, String currency,
			PaymentProviderType providerType, Clock clock) {
		Payment payment = new Payment();
		payment.orderId = Objects.requireNonNull(orderId, "orderId");
		payment.userId = Objects.requireNonNull(userId, "userId");
		payment.amount = validAmount(amount);
		payment.currency = validCurrency(currency);
		payment.providerType = Objects.requireNonNull(providerType, "providerType");
		payment.createdAt = now(clock);
		payment.updatedAt = payment.createdAt;
		return payment;
	}

	/**
	 * Sağlayıcının döndürdüğü ödeme referansını yazar. Referans zaten aynıysa hiçbir şey değişmez ({@code false}).
	 *
	 * @return referans yazıldıysa {@code true}
	 * @throws IllegalArgumentException referans boşsa ya da {@value #PROVIDER_REFERENCE_MAX_LENGTH} karakteri aşıyorsa
	 * @throws IllegalStateException başka bir referans zaten yazılmışsa ya da ödeme sonuçlanmışsa
	 */
	public boolean attachProviderReference(String providerPaymentId, Clock clock) {
		String reference = validReference(providerPaymentId, "providerPaymentId");
		if (this.providerPaymentId != null) {
			if (this.providerPaymentId.equals(reference)) {
				return false;
			}
			throw new IllegalStateException("Payment already has another provider reference");
		}
		if (status.isFinal()) {
			throw new IllegalStateException("Payment is already " + status.dbValue());
		}
		this.providerPaymentId = reference;
		updatedAt = now(clock);
		return true;
	}

	/** {@code initiated} → {@code succeeded}. */
	public TransitionResult succeed(Clock clock) {
		return switch (status) {
			case INITIATED -> {
				status = PaymentStatus.SUCCEEDED;
				updatedAt = now(clock);
				yield TransitionResult.APPLIED;
			}
			case SUCCEEDED -> TransitionResult.ALREADY_IN_STATE;
			case FAILED -> TransitionResult.CONFLICTING_FINAL;
		};
	}

	/**
	 * {@code initiated} → {@code failed} (kodla). Zaten aynı kodla failed ise {@link TransitionResult#ALREADY_IN_STATE},
	 * farklı kodla failed ya da succeeded ise {@link TransitionResult#CONFLICTING_FINAL} (kod değişmez).
	 *
	 * @param failureCode {@code ^[A-Z][A-Z0-9_]{0,63}$} (ör. {@code CARD_DECLINED})
	 * @throws IllegalArgumentException kod biçimi geçersizse (durumdan bağımsız; programlama hatası)
	 */
	public TransitionResult fail(String failureCode, Clock clock) {
		String code = validFailureCode(failureCode);
		return switch (status) {
			case INITIATED -> {
				status = PaymentStatus.FAILED;
				this.failureCode = code;
				updatedAt = now(clock);
				yield TransitionResult.APPLIED;
			}
			case FAILED -> code.equals(this.failureCode) ? TransitionResult.ALREADY_IN_STATE
					: TransitionResult.CONFLICTING_FINAL;
			case SUCCEEDED -> TransitionResult.CONFLICTING_FINAL;
		};
	}

	/**
	 * Aynı sipariş için tekrar gelen oluşturma isteği bu ödemeyle aynı mı (idempotent oluşturma). Tutar
	 * {@code compareTo} ile karşılaştırılır: 10.0 ile 10.00 eşittir.
	 */
	public boolean matches(UUID userId, BigDecimal amount, String currency) {
		return this.userId.equals(userId) && amount != null && this.amount.compareTo(amount) == 0
				&& this.currency.equals(currency);
	}

	/** Boş olmayan, en fazla {@value #PROVIDER_REFERENCE_MAX_LENGTH} karakterlik sağlayıcı kimliği. */
	static String validReference(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		if (value.length() > PROVIDER_REFERENCE_MAX_LENGTH) {
			throw new IllegalArgumentException(
					name + " must be at most " + PROVIDER_REFERENCE_MAX_LENGTH + " characters");
		}
		return value;
	}

	/** DATETIME(6) ile aynı hassasiyet: kaydedilen ve bellekteki değer eşit kalır. */
	static Instant now(Clock clock) {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static BigDecimal validAmount(BigDecimal amount) {
		Objects.requireNonNull(amount, "amount");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
		BigDecimal scaled;
		try {
			scaled = amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw new IllegalArgumentException("Amount must have at most " + AMOUNT_SCALE + " decimal places", ex);
		}
		if (scaled.compareTo(MAX_AMOUNT) > 0) {
			throw new IllegalArgumentException("Amount must not exceed " + MAX_AMOUNT);
		}
		return scaled;
	}

	private static String validCurrency(String currency) {
		if (currency == null || !CURRENCY.matcher(currency).matches()) {
			throw new IllegalArgumentException("Currency must be three upper-case letters");
		}
		return currency;
	}

	private static String validFailureCode(String failureCode) {
		if (failureCode == null || !FAILURE_CODE.matcher(failureCode).matches()) {
			throw new IllegalArgumentException("Failure code must match " + FAILURE_CODE.pattern());
		}
		return failureCode;
	}

}
