package com.kitapsepeti.payment.service;

import java.util.UUID;

import com.kitapsepeti.common.error.DbConstraints;
import com.kitapsepeti.payment.dto.request.CreatePaymentRequest;
import com.kitapsepeti.payment.dto.response.PaymentResponse;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentStatus;
import com.kitapsepeti.payment.exception.PaymentProviderUnavailableException;
import com.kitapsepeti.payment.provider.PaymentProvider;
import com.kitapsepeti.payment.provider.ProviderPayment;
import com.kitapsepeti.payment.provider.ProviderPaymentRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Ödeme kullanım senaryoları. Bilerek transaction'sız: sağlayıcı çağrısı (ağ, saniyeler sürebilir) DB transaction'ı
 * ve satır kilidi tutulurken yapılmaz. Sıra: kayıt ({@link PaymentTransactions#findOrCreate}) → sağlayıcı →
 * referans yazımı ({@link PaymentTransactions#attachReference}).
 */
@Service
public class PaymentService {

	static final String ORDER_CONSTRAINT = "uk_payments_order";

	private final PaymentTransactions transactions;

	private final PaymentProvider provider;

	public PaymentService(PaymentTransactions transactions, PaymentProvider provider) {
		this.transactions = transactions;
		this.provider = provider;
	}

	/** @param created yanıt 201 mi (ödeme bu çağrıda oluşturuldu) yoksa 200 mü */
	public record Result(PaymentResponse payment, boolean created) {
	}

	/**
	 * Siparişin ödemesini oluşturur ya da mevcut olanı döndürür (idempotent, anahtar {@code orderId}).
	 * <p>
	 * Ödeme {@code initiated} ve sağlayıcı referansı yoksa sağlayıcıda oluşturulur; sağlayıcı hata verirse
	 * {@code PAYMENT_PROVIDER_UNAVAILABLE} (503) ve ödeme referanssız kalır, aynı istek tekrarlanınca sağlayıcı
	 * yeniden çağrılır. Sonuçlanmış ödeme için sağlayıcı çağrılmaz.
	 * <p>
	 * Eşzamanlı iki ilk istekte ikinci INSERT {@code uk_payments_order}'ı ihlal eder; transaction geri alınmıştır,
	 * kayıt adımı bir kez YENİ transaction'da tekrarlanır ve var olan ödemeyi aynı kuralla okur. İkinci ihlal
	 * (beklenmez) ve diğer kısıt ihlalleri hata handler'ına gider (409 CONFLICT).
	 */
	public Result create(CreatePaymentRequest request) {
		PaymentTransactions.Initiation initiation;
		try {
			initiation = findOrCreate(request);
		}
		catch (DataIntegrityViolationException ex) {
			if (!DbConstraints.isViolated(ex, ORDER_CONSTRAINT)) {
				throw ex;
			}
			initiation = findOrCreate(request);
		}
		Payment payment = initiation.payment();
		if (payment.getStatus() == PaymentStatus.INITIATED && payment.getProviderPaymentId() == null) {
			ProviderPayment providerPayment = createAtProvider(payment);
			payment = this.transactions.attachReference(payment.getId(), providerPayment.providerPaymentId());
		}
		return new Result(PaymentResponse.of(payment), initiation.created());
	}

	public PaymentResponse get(UUID paymentId) {
		return PaymentResponse.of(this.transactions.find(paymentId));
	}

	private PaymentTransactions.Initiation findOrCreate(CreatePaymentRequest request) {
		return this.transactions.findOrCreate(request.orderId(), request.userId(), request.amount(),
				request.currency());
	}

	/** Sağlayıcının her hatası ve geçersiz referans (boş / 128 karakterden uzun) aynı 503'e çevrilir. */
	private ProviderPayment createAtProvider(Payment payment) {
		ProviderPayment providerPayment;
		try {
			providerPayment = this.provider
				.create(new ProviderPaymentRequest(payment.getId(), payment.getAmount(), payment.getCurrency()));
		}
		catch (RuntimeException ex) {
			throw new PaymentProviderUnavailableException(ex);
		}
		String reference = (providerPayment != null) ? providerPayment.providerPaymentId() : null;
		if (reference == null || reference.isBlank() || reference.length() > Payment.PROVIDER_REFERENCE_MAX_LENGTH) {
			throw new PaymentProviderUnavailableException(
					new IllegalStateException("Payment provider returned an invalid payment reference"));
		}
		return providerPayment;
	}

}
