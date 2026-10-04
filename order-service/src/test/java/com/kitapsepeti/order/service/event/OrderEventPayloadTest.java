package com.kitapsepeti.order.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class OrderEventPayloadTest {

	@Test
	void payloadRecordFieldOrderIsContractual() {
		assertThat(fields(OrderPaidEvent.class)).containsExactly("eventId", "eventVersion", "orderId", "userId",
				"paymentId", "totalAmount", "currency", "itemCount", "occurredAt");
		assertThat(fields(OrderFailedEvent.class)).containsExactly("eventId", "eventVersion", "orderId", "userId",
				"failureCode", "occurredAt");
		assertThat(fields(CartCheckedOutEvent.class)).containsExactly("eventId", "eventVersion", "cartId", "userId",
				"orderId", "occurredAt");
	}

	@Test
	void allPayloadVersionsAreOne() {
		assertThat(OrderPaidEvent.VERSION).isEqualTo(1);
		assertThat(OrderFailedEvent.VERSION).isEqualTo(1);
		assertThat(CartCheckedOutEvent.VERSION).isEqualTo(1);
	}

	private static String[] fields(Class<?> recordType) {
		return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new);
	}

}
