package com.kitapsepeti.order.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.kitapsepeti.order.gateway.Rejected;
import com.kitapsepeti.order.gateway.Unavailable;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Gateway'lerin tek çağrı yolu: circuit breaker izni, çağrı, yanıt doğrulama, sınıflandırma ve tek satır log.
 *
 * <p>Sınıflandırma ({@link CallOutcome}):
 * <ul>
 * <li>2xx + geçerli gövde: {@code Success}; circuit breaker başarı.</li>
 * <li>4xx: {@code Problem}; circuit breaker BAŞARI sayılır (karşı servis ayakta ve cevap verdi; iş hatası devreyi
 * açmaz).</li>
 * <li>Bağlantı kurulamadı (reddedildi, bağlantı zaman aşımı, adres çözülemedi): {@code NotSent}; hata.</li>
 * <li>Okuma zaman aşımı, kopma, 5xx, 3xx, okunamayan/sözleşmeye uymayan 2xx: {@code Failed}; hata.</li>
 * <li>Circuit breaker açık: {@code NotSent}, istek gönderilmez.</li>
 * </ul>
 *
 * <p>Log: istemci, işlem, sonuç tipi, HTTP durumu, süre ve teknik hatada exception sınıf adı. Exception mesajı (Feign
 * mesajı URL'yi, dolayısıyla sipariş id'sini içerir), gövde, id, tutar ve anahtar YAZILMAZ.
 */
@Component
public class RemoteCalls {

	private static final Logger log = LoggerFactory.getLogger(RemoteCalls.class);

	static final String CIRCUIT_OPEN = "CircuitOpen";

	private final DownstreamCircuitBreakers breakers;

	public RemoteCalls(DownstreamCircuitBreakers breakers) {
		this.breakers = breakers;
	}

	/**
	 * @param request Feign çağrısı
	 * @param validator 2xx gövdesini doğrular; sözleşmeye uymuyorsa {@link InvalidResponseException} atar
	 * @param mapper sınıflandırılmış sonucu gateway sonucuna çevirir
	 */
	public <T, R> R execute(Downstream downstream, String operation, Supplier<ResponseEntity<T>> request,
			Consumer<T> validator, Function<CallOutcome<T>, R> mapper) {
		CircuitBreaker breaker = this.breakers.get(downstream);
		if (!breaker.tryAcquirePermission()) {
			CallOutcome<T> outcome = new CallOutcome.NotSent<>();
			R result = mapper.apply(outcome);
			log(downstream, operation, outcome, result, null, 0, CIRCUIT_OPEN);
			return result;
		}
		long start = System.nanoTime();
		Integer status = null;
		String cause = null;
		CallOutcome<T> outcome;
		try {
			ResponseEntity<T> response = request.get();
			status = response.getStatusCode().value();
			T body = response.getBody();
			if (body == null) {
				throw new InvalidResponseException("Remote response body is empty");
			}
			validator.accept(body);
			breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
			outcome = new CallOutcome.Success<>(status, body);
		}
		catch (RemoteProblemException ex) {
			status = ex.status();
			if (status >= 400 && status < 500) {
				breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
				outcome = new CallOutcome.Problem<>(status, ex.code(), ex.bookIds());
			}
			else {
				breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
				outcome = new CallOutcome.Failed<>();
			}
		}
		catch (RetryableException ex) {
			breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
			cause = causeName(ex);
			outcome = notConnected(ex) ? new CallOutcome.NotSent<>() : new CallOutcome.Failed<>();
		}
		catch (RuntimeException ex) {
			breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
			if (ex instanceof FeignException feign && feign.status() > 0) {
				status = feign.status();
			}
			cause = causeName(ex);
			outcome = new CallOutcome.Failed<>();
		}
		R result = mapper.apply(outcome);
		log(downstream, operation, outcome, result, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
				cause);
		return result;
	}

	/** Bağlantı hiç kurulamadıysa istek karşıya ulaşmamıştır; okuma aşamasındaki her hata "gönderildi" sayılır. */
	static boolean notConnected(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof ConnectException || t instanceof HttpConnectTimeoutException
					|| t instanceof UnknownHostException || t instanceof NoRouteToHostException) {
				return true;
			}
		}
		return false;
	}

	private static String causeName(RuntimeException ex) {
		Throwable shown = (ex instanceof FeignException && ex.getCause() != null) ? ex.getCause() : ex;
		return shown.getClass().getSimpleName();
	}

	private static void log(Downstream downstream, String operation, CallOutcome<?> outcome, Object result,
			Integer status, long durationMs, String cause) {
		boolean technical = outcome instanceof CallOutcome.NotSent || outcome instanceof CallOutcome.Failed;
		String detail = (cause != null) ? ", cause=" + cause : "";
		String shownStatus = (status != null) ? status.toString() : "-";
		String resultType = result.getClass().getSimpleName();
		if (technical || result instanceof Unavailable || result instanceof Rejected) {
			log.warn("Remote call {} {} -> {} (status={}, durationMs={}{})", downstream.id(), operation, resultType,
					shownStatus, durationMs, detail);
		}
		else {
			log.info("Remote call {} {} -> {} (status={}, durationMs={})", downstream.id(), operation, resultType,
					shownStatus, durationMs);
		}
	}

}
