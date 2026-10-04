package com.kitapsepeti.payment.provider.mock;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.kitapsepeti.payment.config.PaymentProperties;
import com.kitapsepeti.payment.entity.Payment;
import com.kitapsepeti.payment.entity.PaymentProviderType;
import com.kitapsepeti.payment.entity.PaymentStatus;
import com.kitapsepeti.payment.provider.MockOutcomeRule;
import com.kitapsepeti.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mock sağlayıcının webhook'unu uygulamanın kendi webhook ucuna gönderir; gerçek sağlayıcıda bu isteği sağlayıcının
 * sunucusu yapar.
 * <p>
 * Tetikleme: {@link MockPaymentReadyEvent} referansı yazan transaction commit edildikten sonra dinlenir ve gönderim
 * {@code app.payment.mock.delay} sonrasına, bu sınıfın kendi küçük zamanlayıcısına planlanır; istek thread'i
 * beklemez. Planlanmış gönderim sayısı {@code app.payment.mock.dispatch.queue-capacity} ile sınırlıdır: dolunca yeni
 * gönderim düşürülür (WARN), ödeme {@code initiated} kalır ve {@link MockRecoveryJob} toparlar. Zamanlayıcı bean
 * değildir; {@code @Scheduled} görevleri onu kullanmaz.
 * <p>
 * Gönderim ({@link #send}): ödeme kilitsiz okunur, yalnızca referanslı {@code initiated} mock ödeme gönderilir.
 * Gövde bir kez serileştirilir, aynı baytlar imzalanır ve gönderilir. Tekrar yok: 4xx kalıcı ret sayılır, 5xx /
 * bağlantı hatası / zaman aşımı kurtarma görevine kalır. Hiçbir durumda exception dışarı çıkmaz.
 * <p>
 * Loglar ödeme id'si, referans, olay kimliği, tutar, imza, gövde ve adres içermez.
 */
public class MockWebhookDispatcher implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(MockWebhookDispatcher.class);

	static final String WEBHOOK_PATH = "/webhooks/mock";

	static final Duration CONNECT_TIMEOUT = Duration.ofMillis(1000);

	static final Duration READ_TIMEOUT = Duration.ofMillis(2000);

	/** Ayrı yönetim portu açılırsa onun sunucu olayı; webhook ucu orada değil. */
	private static final String MANAGEMENT_NAMESPACE = "management";

	/** Gönderimin sonucu; çağıran (kurtarma görevi, testler) sayar. */
	public enum Delivery {

		/** Webhook ucu 2xx döndü (ilk işleme ya da tekrar). */
		DELIVERED,
		/** Gönderilecek bir şey yok: ödeme yok, sonuçlanmış, referanssız ya da mock değil. */
		SKIPPED,
		/** Webhook ucu 4xx döndü; tekrar denenmez. */
		REJECTED,
		/** 5xx, bağlantı hatası, zaman aşımı ya da beklenmeyen hata; kurtarma görevi yeniden dener. */
		FAILED

	}

	private final PaymentRepository payments;

	private final MockOutcomeRule outcomeRule;

	private final MockWebhookSigner signer;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	private final Duration delay;

	private final String configuredUrl;

	private final int queueCapacity;

	private final HttpClient httpClient;

	private final RestClient restClient;

	/** Otomatik gönderim kapalıyken null; {@link #send} yine çalışır (kurtarma görevi). */
	private final ThreadPoolTaskScheduler scheduler;

	private final AtomicInteger pending = new AtomicInteger();

	private volatile String localUrl;

	public MockWebhookDispatcher(PaymentRepository payments, MockOutcomeRule outcomeRule, MockWebhookSigner signer,
			JsonMapper jsonMapper, Clock clock, PaymentProperties.Mock settings) {
		this.payments = payments;
		this.outcomeRule = outcomeRule;
		this.signer = signer;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.delay = settings.delay();
		this.configuredUrl = (settings.webhookUrl() == null || settings.webhookUrl().isBlank()) ? null
				: settings.webhookUrl();
		this.queueCapacity = settings.dispatch().queueCapacity();
		this.httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(this.httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.scheduler = settings.dispatch().enabled() ? newScheduler() : null;
	}

	private static ThreadPoolTaskScheduler newScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("mock-webhook-");
		scheduler.setDaemon(true);
		scheduler.setWaitForTasksToCompleteOnShutdown(false);
		scheduler.initialize();
		return scheduler;
	}

	/**
	 * Referans yazıldı ve commit edildi: gönderimi gecikmeyle planlar. İstek thread'inde çalışır; exception atmaz
	 * (commit sonrası dinleyicinin hatası isteğe yansırdı).
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPaymentReady(MockPaymentReadyEvent event) {
		if (this.scheduler == null) {
			return;
		}
		if (this.pending.incrementAndGet() > this.queueCapacity) {
			this.pending.decrementAndGet();
			log.warn("Mock webhook dispatch queue is full; webhook dropped, recovery job will resend it");
			return;
		}
		UUID paymentId = event.paymentId();
		try {
			this.scheduler.schedule(() -> {
				try {
					send(paymentId);
				}
				finally {
					this.pending.decrementAndGet();
				}
			}, this.scheduler.getClock().instant().plus(this.delay));
		}
		catch (TaskRejectedException ex) {
			this.pending.decrementAndGet();
			log.warn("Mock webhook dispatch rejected by scheduler; recovery job will resend it");
		}
	}

	/** Adres yapılandırılmamışsa uygulamanın gerçekten dinlediği port kullanılır (rastgele port dahil). */
	@EventListener
	public void onWebServerInitialized(WebServerInitializedEvent event) {
		if (MANAGEMENT_NAMESPACE.equals(event.getApplicationContext().getServerNamespace())) {
			return;
		}
		this.localUrl = "http://localhost:" + event.getWebServer().getPort() + WEBHOOK_PATH;
	}

	/**
	 * Ödemenin mock webhook'unu bir kez gönderir (tekrar yok). Olay kimliği ödeme başına sabit olduğundan aynı
	 * ödemeyi birden çok kez göndermek zararsızdır: webhook ucu tekrarı 204 ile yok sayar.
	 */
	public Delivery send(UUID paymentId) {
		try {
			return deliver(paymentId);
		}
		catch (RuntimeException ex) {
			log.warn("Mock webhook delivery failed (cause={})", ex.getClass().getSimpleName());
			return Delivery.FAILED;
		}
	}

	private Delivery deliver(UUID paymentId) {
		Payment payment = this.payments.findById(paymentId).orElse(null);
		if (payment == null || payment.getProviderType() != PaymentProviderType.MOCK
				|| payment.getStatus() != PaymentStatus.INITIATED || payment.getProviderPaymentId() == null) {
			return Delivery.SKIPPED;
		}
		String url = webhookUrl();
		if (url == null) {
			log.warn("Mock webhook delivery failed (webhook URL not known yet)");
			return Delivery.FAILED;
		}
		byte[] body = this.jsonMapper
			.writeValueAsBytes(MockWebhookPayload.of(payment, this.outcomeRule.outcomeFor(payment.getAmount())));
		long timestamp = this.clock.instant().getEpochSecond();
		String signature = this.signer.sign(timestamp, body);
		try {
			ResponseEntity<Void> response = this.restClient.post()
				.uri(url)
				.contentType(MediaType.APPLICATION_JSON)
				.header(MockWebhookSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
				.header(MockWebhookSigner.SIGNATURE_HEADER, signature)
				.body(body)
				.retrieve()
				.toBodilessEntity();
			int status = response.getStatusCode().value();
			if (!response.getStatusCode().is2xxSuccessful()) {
				log.warn("Mock webhook delivery failed (status={})", status);
				return Delivery.FAILED;
			}
			log.debug("Mock webhook delivered (status={})", status);
			return Delivery.DELIVERED;
		}
		catch (HttpClientErrorException ex) {
			log.warn("Mock webhook rejected (status={})", ex.getStatusCode().value());
			return Delivery.REJECTED;
		}
		catch (HttpServerErrorException ex) {
			log.warn("Mock webhook delivery failed (status={})", ex.getStatusCode().value());
			return Delivery.FAILED;
		}
		catch (ResourceAccessException ex) {
			Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
			log.warn("Mock webhook delivery failed (cause={})", cause.getClass().getSimpleName());
			return Delivery.FAILED;
		}
	}

	private String webhookUrl() {
		return (this.configuredUrl != null) ? this.configuredUrl : this.localUrl;
	}

	/** Planlanmış ama henüz bitmemiş gönderim sayısı (testler için). */
	int pendingCount() {
		return this.pending.get();
	}

	@Override
	public void destroy() {
		if (this.scheduler != null) {
			this.scheduler.shutdown();
		}
		this.httpClient.shutdownNow();
	}

}
