package com.kitapsepeti.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import com.kitapsepeti.common.outbox.OutboxEvent;
import com.kitapsepeti.common.outbox.OutboxRelay;
import com.kitapsepeti.common.outbox.OutboxRepository;
import com.kitapsepeti.common.outbox.OutboxService;
import com.kitapsepeti.order.controller.OrderController;
import com.kitapsepeti.order.entity.Order;
import com.kitapsepeti.order.entity.OrderItem;
import com.kitapsepeti.order.entity.OrderStatusHistory;
import com.kitapsepeti.order.outbox.EventRoutingKeys;
import com.kitapsepeti.order.repository.OrderRepository;
import com.kitapsepeti.order.outbox.OutboxPublisher;
import feign.RequestInterceptor;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;

/**
 * Bağlam açılır, Flyway V1 uygulanır, sipariş entity'leriyle ddl validate geçer. Uçlar {@code controller} test
 * paketinde, actuator yüzeyi {@code config.ActuatorHealthTest}'te, istemciler {@code client} test paketinde.
 */
class OrderServiceApplicationTests extends ApiTestSupport {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void contextLoadsAndFlywayAppliedV1() {
		assertThat(jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class))
			.containsExactly("1");
	}

	@Test
	void clockIsUtc() {
		assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
	}

	/** ddl validate'in kapsadığı entity'ler: sipariş aggregate'i ve ortak outbox. Kalem/geçmiş için ayrı repository yok. */
	@Test
	void orderAndOutboxEntitiesAndRepositoriesAreMapped() {
		assertThat(entityManagerFactory.getMetamodel().getEntities())
			.extracting(entity -> (Object) entity.getJavaType())
			.containsExactlyInAnyOrder(Order.class, OrderItem.class, OrderStatusHistory.class, OutboxEvent.class);
		assertThat(context.getBeansOfType(Repository.class).values())
			.extracting(repository -> (Object) repositoryInterface(repository))
			.containsExactlyInAnyOrder(OrderRepository.class, OutboxRepository.class);
	}

	private static Class<?> repositoryInterface(Object proxy) {
		for (Class<?> type : ClassUtils.getAllInterfacesAsSet(proxy)) {
			if (type == OrderRepository.class || type == OutboxRepository.class) {
				return type;
			}
		}
		return proxy.getClass();
	}

	@Test
	void onlyOrderControllerIsExposed() {
		assertThat(context.getBeansWithAnnotation(Controller.class).values().stream()
			.<Class<?>>map(ClassUtils::getUserClass)
			.filter(type -> type.getPackageName().startsWith("com.kitapsepeti.order"))
			.toList()).containsExactly(OrderController.class);
	}

	/** Ortak outbox bağlı: exchange, yazıcı ve order'ın routing key eşlemesine bağlı yayıncı; relay testte kapalı. */
	@Test
	void outboxIsWiredWithOrderPublisher() {
		assertThat(context.getBean(com.kitapsepeti.common.outbox.OutboxPublisher.class))
			.isInstanceOf(OutboxPublisher.class);
		assertThat(context.getBeansOfType(OutboxService.class)).hasSize(1);
		assertThat(context.getBean(TopicExchange.class).getName()).isEqualTo("kitapsepeti.events");
		assertThat(context.getBeansOfType(OutboxRelay.class)).isEmpty();
	}

	/** Order olayları mevcut adlandırma kuralıyla yönlendirilir. */
	@Test
	void routingKeysAreMapped() {
		assertThat(EventRoutingKeys.forEventType("OrderPaid")).isEqualTo("order.paid");
		assertThat(EventRoutingKeys.forEventType("OrderFailed")).isEqualTo("order.failed");
		assertThat(EventRoutingKeys.forEventType("CartCheckedOut")).isEqualTo("cart.checked-out");
	}

	/** Resilience4j yalnızca çekirdek kütüphane olarak var; Spring Cloud'un otomatik circuit breaker katmanları yok. */
	@Test
	void springCloudCircuitBreakerLayersAreDisabled() {
		assertThat(context.getBeansOfType(CircuitBreakerFactory.class)).isEmpty();
		assertThat(context.getBeansOfType(RequestInterceptor.class)).as("global Feign interceptor").isEmpty();
	}

}
