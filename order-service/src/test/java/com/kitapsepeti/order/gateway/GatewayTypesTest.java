package com.kitapsepeti.order.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.order.client.cart.CartSnapshotRequest;
import com.kitapsepeti.order.client.catalog.ReserveStockRequest;
import com.kitapsepeti.order.client.payment.CreatePaymentRequest;
import org.junit.jupiter.api.Test;

/**
 * Domain'e bakan sınır: gateway imzaları ve sonuç tipleri yalnızca {@code java.*} ve bu paketin tiplerini kullanır (Feign,
 * Spring, Resilience4j ya da istemci DTO'su sızmaz); sonuçlar sealed; id, tutar ve kitap içeren tiplerin
 * {@code toString()}'i maskeli.
 */
class GatewayTypesTest {

	private static final List<Class<?>> GATEWAYS = List.of(CartGateway.class, CatalogGateway.class,
			PaymentGateway.class);

	private static final List<Class<?>> RESULTS = List.of(CartSnapshotResult.class, BookLookupResult.class,
			ReserveResult.class, CommitResult.class, ReleaseResult.class, PaymentInitiationResult.class);

	@Test
	void gatewaySignaturesUseOnlyJavaAndGatewayTypes() {
		List<Type> types = new ArrayList<>();
		for (Class<?> gateway : GATEWAYS) {
			assertThat(gateway).isInterface();
			for (Method method : gateway.getDeclaredMethods()) {
				types.add(method.getGenericReturnType());
				types.addAll(List.of(method.getGenericParameterTypes()));
				types.addAll(List.of(method.getGenericExceptionTypes()));
			}
		}
		for (Class<?> result : RESULTS) {
			for (Class<?> subtype : result.getPermittedSubclasses()) {
				for (RecordComponent component : subtype.getRecordComponents()) {
					types.add(component.getGenericType());
				}
			}
		}
		assertThat(types).allSatisfy(GatewayTypesTest::assertDomainType);
	}

	private static void assertDomainType(Type type) {
		if (type instanceof ParameterizedType parameterized) {
			assertDomainType(parameterized.getRawType());
			for (Type argument : parameterized.getActualTypeArguments()) {
				assertDomainType(argument);
			}
			return;
		}
		Class<?> raw = (Class<?>) type;
		assertThat(raw.isPrimitive() || raw.getPackageName().startsWith("java.")
				|| raw.getPackageName().equals(GatewayTypesTest.class.getPackageName()))
			.as("gateway sınırında izin verilmeyen tip: %s", raw.getName())
			.isTrue();
	}

	@Test
	void resultsAreSealedAndEveryOutcomeIsARecord() {
		for (Class<?> result : RESULTS) {
			assertThat(result.isSealed()).as(result.getSimpleName()).isTrue();
			assertThat(result.getPermittedSubclasses()).allSatisfy(subtype -> assertThat(subtype.isRecord()).isTrue());
		}
		assertThat(ReserveResult.class.getPermittedSubclasses()).contains(NotPerformed.class, Unknown.class);
		assertThat(CommitResult.class.getPermittedSubclasses()).contains(NotPerformed.class, Unknown.class);
		assertThat(ReleaseResult.class.getPermittedSubclasses()).contains(NotPerformed.class, Unknown.class);
		assertThat(PaymentInitiationResult.class.getPermittedSubclasses()).contains(NotPerformed.class, Unknown.class);
		assertThat(CartSnapshotResult.class.getPermittedSubclasses()).contains(Unavailable.class)
			.doesNotContain(NotPerformed.class, Unknown.class);
		assertThat(BookLookupResult.class.getPermittedSubclasses()).contains(Unavailable.class)
			.doesNotContain(NotPerformed.class, Unknown.class);
	}

	@Test
	void toStringsHideIdentifiersTitlesAndAmounts() {
		UUID id = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		BigDecimal amount = new BigDecimal("987.65");
		List<Object> values = List.of(new StockLine(id, 3), new CartSnapshotResult.Snapshot(id, List.of(new StockLine(other, 1))),
				new CatalogBook(id, "Gizli Baslik", amount, "TRY", true),
				new BookLookupResult.Found(Map.of(id, new CatalogBook(id, "Gizli Baslik", amount, "TRY", true)),
						Set.of(other)),
				new ReserveResult.Insufficient(List.of(id)), new ReserveResult.NotSellable(List.of(id)),
				new ReserveResult.Reserved(Instant.parse("2026-10-04T10:15:00Z")),
				new PaymentInitiationResult.Initiated(id, PaymentState.INITIATED), new CartSnapshotRequest(id),
				new ReserveStockRequest(id, List.of(new ReserveStockRequest.Item(other, 2))),
				new CreatePaymentRequest(id, other, amount, "TRY"));

		assertThat(values).allSatisfy(value -> assertThat(value.toString()).doesNotContain(id.toString())
			.doesNotContain(other.toString())
			.doesNotContain("987.65")
			.doesNotContain("Gizli"));
	}

}
