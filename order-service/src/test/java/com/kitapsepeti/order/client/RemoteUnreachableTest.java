package com.kitapsepeti.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.kitapsepeti.order.gateway.NotPerformed;
import com.kitapsepeti.order.gateway.StockLine;
import com.kitapsepeti.order.gateway.Unavailable;
import com.kitapsepeti.order.support.StubServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.json.JsonCompareMode;

/**
 * Bağımlı servisler kapalıyken (bağlantı reddedilir): yazma işlemleri {@link NotPerformed} (istek karşıya ulaşmadı,
 * telafi gerekmez), okumalar {@link Unavailable}. Order'ın readiness'ı etkilenmez.
 */
@ExtendWith(OutputCaptureExtension.class)
class RemoteUnreachableTest extends ClientTestSupport {

	private final UUID orderId = UUID.randomUUID();

	@Test
	void connectionRefusedIsNotPerformedForWritesAndUnavailableForReads(CapturedOutput output) {
		STUBS.forEach(StubServer::stop);

		assertThat(this.cartGateway.snapshot(UUID.randomUUID())).isEqualTo(new Unavailable());
		assertThat(this.catalogGateway.lookup(List.of(UUID.randomUUID()))).isEqualTo(new Unavailable());
		assertThat(this.catalogGateway.reserve(this.orderId, List.of(new StockLine(UUID.randomUUID(), 1))))
			.isEqualTo(new NotPerformed());
		assertThat(this.catalogGateway.commit(this.orderId)).isEqualTo(new NotPerformed());
		assertThat(this.catalogGateway.release(this.orderId)).isEqualTo(new NotPerformed());
		assertThat(this.paymentGateway.initiate(this.orderId, UUID.randomUUID(), new BigDecimal("10.00"), "TRY"))
			.isEqualTo(new NotPerformed());

		assertThat(output.getOut()).contains("Remote call catalog reserve -> NotPerformed (status=-, durationMs=")
			.contains("cause=ConnectException");
	}

	/** Bağlantı hataları circuit breaker'da hata sayılır (teknik hata). */
	@Test
	void connectionRefusedCountsAsCircuitBreakerFailure() {
		PAYMENT.stop();

		this.paymentGateway.initiate(this.orderId, UUID.randomUUID(), new BigDecimal("10.00"), "TRY");

		CircuitBreaker.Metrics metrics = this.breakers.get(Downstream.PAYMENT).getMetrics();
		assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(1);
	}

	@Test
	void readinessStaysUpWhileAllRemotesAreDownAndCircuitsAreOpen() throws Exception {
		STUBS.forEach(StubServer::stop);
		for (Downstream downstream : Downstream.values()) {
			this.breakers.get(downstream).transitionToOpenState();
		}

		this.mockMvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
		this.mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.LENIENT));
	}

}
