package com.kitapsepeti.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import com.kitapsepeti.cart.TestcontainersConfiguration;
import com.kitapsepeti.cart.entity.Cart;
import com.kitapsepeti.cart.entity.CartItem;
import com.kitapsepeti.cart.entity.CartStatus;
import com.kitapsepeti.cart.support.SqlCapture;
import com.kitapsepeti.common.error.DbConstraints;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.metamodel.EntityType;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Entity eşlemeleri ve repository sorguları (gerçek MySQL, Flyway V1, ddl-auto validate).
 * Her test kendi transaction'ında koşar ve geri alınır; DB'deki değer aynı transaction'da JDBC ile okunur.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CartRepositoryTest {

	private static final Clock T1 = Clock.fixed(Instant.parse("2026-03-01T10:15:30.123456789Z"), ZoneOffset.UTC);

	private static final Clock T2 = Clock.fixed(Instant.parse("2026-03-01T10:16:00Z"), ZoneOffset.UTC);

	/** DB'deki BINARY(16) sırası = işaretsiz msb, sonra işaretsiz lsb ({@code UUID.compareTo} işaretli karşılaştırır). */
	private static final Comparator<UUID> DB_ORDER = (a, b) -> {
		int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
		return (msb != 0) ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
	};

	@Autowired
	private CartRepository carts;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private Statistics statistics;

	private PersistenceUnitUtil persistenceUnitUtil;

	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
	}

	@Test
	void contextStartsWithSchemaValidation() {
		Object ddlAuto = entityManager.getEntityManagerFactory().getProperties().get("hibernate.hbm2ddl.auto");

		assertThat(ddlAuto).isEqualTo("validate");
		assertThat(entityManager.getMetamodel().getEntities()).extracting(EntityType::getName)
			.containsExactlyInAnyOrder("Cart", "CartItem");
	}

	@Test
	void cartAndItemsAreSavedByCascadeWithUuidV7Ids() {
		Cart cart = Cart.openFor(userId, T1);
		CartItem first = cart.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00"), "TRY", "A", null, T1);
		CartItem second = cart.addItem(UUID.randomUUID(), 2, new BigDecimal("20.00"), "TRY", "B", null, T1);

		carts.saveAndFlush(cart);

		assertThat(Stream.of(cart.getId(), first.getId(), second.getId()))
			.allSatisfy(id -> assertThat(id.version()).isEqualTo(7));
		assertThat(jdbc.queryForObject("SELECT status FROM carts WHERE id = ?", String.class, bytes(cart.getId())))
			.isEqualTo("active");
		assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(user_id) FROM carts WHERE id = ?", String.class,
				bytes(cart.getId())))
			.isEqualTo(userId.toString());
		assertThat(jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM cart_items WHERE cart_id = ?", String.class,
				bytes(cart.getId())))
			.containsExactlyInAnyOrder(first.getId().toString(), second.getId().toString());
	}

	@Test
	void timestampsAreStoredInUtcAndItemChangeStampsItemAndCartWithClock() {
		Cart cart = Cart.openFor(userId, T1);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, T1);
		carts.saveAndFlush(cart);

		// Saat 10:15 UTC verildi; DB'de de 10:15 (JVM saat dilimi UTC+3 olsa bile).
		assertThat(dbTime("carts", "created_at", cart.getId())).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("carts", "updated_at", cart.getId())).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("cart_items", "added_at", item.getId())).isEqualTo("2026-03-01 10:15:30.123456");
		assertThat(dbTime("cart_items", "updated_at", item.getId())).isEqualTo("2026-03-01 10:15:30.123456");

		item.changeQuantity(3, T2);
		carts.flush();

		assertThat(dbTime("cart_items", "updated_at", item.getId())).isEqualTo("2026-03-01 10:16:00.000000");
		assertThat(dbTime("cart_items", "added_at", item.getId())).isEqualTo("2026-03-01 10:15:30.123456");
		// Satır değişince sepetin "son işlem" anı da aynı saatle yenilenir (DB'nin ON UPDATE'i değil, uygulama saati).
		assertThat(dbTime("carts", "updated_at", cart.getId())).isEqualTo("2026-03-01 10:16:00.000000");
		assertThat(dbTime("carts", "created_at", cart.getId())).isEqualTo("2026-03-01 10:15:30.123456");
	}

	@Test
	void cartUpdatedAtIsStampedWithClockOnStatusChange() {
		Cart cart = carts.saveAndFlush(Cart.openFor(userId, T1));

		cart.checkout(T2);
		carts.flush();

		assertThat(dbTime("carts", "updated_at", cart.getId())).isEqualTo("2026-03-01 10:16:00.000000");
		assertThat(dbTime("carts", "created_at", cart.getId())).isEqualTo("2026-03-01 10:15:30.123456");
	}

	@Test
	void findByUserIdAndStatusLoadsItemsInOneQueryOrderedByAddedAtThenId() {
		UUID late = UUID.randomUUID();
		UUID early = UUID.randomUUID();
		UUID earlyToo = UUID.randomUUID();
		Cart cart = Cart.openFor(userId, T1);
		cart.addItem(late, 1, BigDecimal.TEN, null, "Geç", null, T2);
		CartItem earlyItem = cart.addItem(early, 1, BigDecimal.TEN, null, "Erken", null, T1);
		CartItem earlyTooItem = cart.addItem(earlyToo, 1, BigDecimal.TEN, null, "Erken 2", null, T1);
		carts.saveAndFlush(cart);
		List<UUID> sameTimeByIdOrder = Stream.of(earlyItem, earlyTooItem)
			.sorted(Comparator.comparing(CartItem::getId, DB_ORDER))
			.map(CartItem::getBookId)
			.toList();
		entityManager.clear();
		statistics.clear();

		Cart loaded = carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE).orElseThrow();

		assertThat(persistenceUnitUtil.isLoaded(loaded, "items")).isTrue();
		assertThat(loaded.getItems()).extracting(CartItem::getBookId)
			.containsExactly(sameTimeByIdOrder.get(0), sameTimeByIdOrder.get(1), late);
		assertThat(statistics.getPrepareStatementCount()).as("cart + items in one statement").isEqualTo(1);
	}

	@Test
	void plainFindByIdLoadsItemsLazilyWithSecondQuery() {
		Cart cart = Cart.openFor(userId, T1);
		cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, T1);
		carts.saveAndFlush(cart);
		entityManager.clear();
		statistics.clear();

		Cart loaded = carts.findById(cart.getId()).orElseThrow();
		assertThat(persistenceUnitUtil.isLoaded(loaded, "items")).isFalse();
		assertThat(loaded.getItems()).hasSize(1);

		// Kontrol: entity graph olmadan iki sorgu; yukarıdaki tek sorgu sonucu anlamlı.
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
	}

	@Test
	void findByUserIdAndStatusIgnoresOtherStatusesAndUsers() {
		Cart historic = Cart.openFor(userId, T1);
		historic.checkout(T1);
		carts.saveAndFlush(historic);
		carts.saveAndFlush(Cart.openFor(UUID.randomUUID(), T1));

		assertThat(carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).isEmpty();
		assertThat(carts.findByUserIdAndStatus(userId, CartStatus.CHECKED_OUT)).get()
			.extracting(Cart::getId).isEqualTo(historic.getId());
	}

	@Test
	void removeItemDeletesRowAndClearDeletesAllRowsButKeepsCart() {
		Cart cart = Cart.openFor(userId, T1);
		CartItem first = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, T1);
		CartItem second = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "B", null, T1);
		carts.saveAndFlush(cart);

		assertThat(cart.removeItem(first.getId(), T2)).isTrue();
		carts.flush();

		assertThat(itemIds(cart.getId())).containsExactly(second.getId().toString());

		cart.clear(T2);
		carts.flush();

		assertThat(itemIds(cart.getId())).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE id = ?", Integer.class, bytes(cart.getId())))
			.isEqualTo(1);
	}

	@Test
	void secondActiveCartForSameUserViolatesUkCartsActiveUser() {
		carts.saveAndFlush(Cart.openFor(userId, T1));

		Throwable thrown = catchThrowable(() -> carts.saveAndFlush(Cart.openFor(userId, T2)));

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.normalize(DbConstraints.nameOf(thrown))).isEqualTo("uk_carts_active_user");
		assertThat(DbConstraints.isViolated(thrown, "uk_carts_active_user")).isTrue();
	}

	@Test
	void checkedOutCartAllowsNewActiveCartAndCannotBeCheckedOutAgain() {
		Cart first = carts.saveAndFlush(Cart.openFor(userId, T1));

		first.checkout(T2);
		carts.flush();
		Cart second = carts.saveAndFlush(Cart.openFor(userId, T2));

		assertThat(jdbc.queryForObject("SELECT status FROM carts WHERE id = ?", String.class, bytes(first.getId())))
			.isEqualTo("checked_out");
		assertThat(carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).get()
			.extracting(Cart::getId).isEqualTo(second.getId());
		assertThatThrownBy(() -> first.checkout(T2)).isInstanceOf(IllegalStateException.class);
	}

	/**
	 * Hibernate flush sırası INSERT → UPDATE → DELETE: checkout (UPDATE) ile yeni sepet (INSERT) aynı flush'ta
	 * olursa yeni sepet eskisi hâlâ 'active' iken yazılır. Servis checkout'tan sonra flush etmeli (yukarıdaki test).
	 */
	@Test
	void newActiveCartInSameFlushAsCheckoutViolatesUkCartsActiveUser() {
		Cart first = carts.saveAndFlush(Cart.openFor(userId, T1));

		first.checkout(T2);
		Throwable thrown = catchThrowable(() -> carts.saveAndFlush(Cart.openFor(userId, T2)));

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.isViolated(thrown, "uk_carts_active_user")).isTrue();
	}

	@Test
	void sameBookTwiceIsRejectedByDomainAndByUkCartItemsCartBook() {
		UUID bookId = UUID.randomUUID();
		Cart cart = Cart.openFor(userId, T1);
		cart.addItem(bookId, 1, BigDecimal.TEN, null, "A", null, T1);
		carts.saveAndFlush(cart);

		assertThatThrownBy(() -> cart.addItem(bookId, 1, BigDecimal.TEN, null, "A", null, T1))
			.isInstanceOf(IllegalStateException.class);
		Throwable thrown = catchThrowable(() -> entityManager.createNativeQuery("""
				INSERT INTO cart_items (id, cart_id, book_id, quantity, unit_price_snapshot, title_snapshot)
				VALUES (?1, ?2, ?3, 1, 10.00, 'Ham')
				""")
			.setParameter(1, bytes(UUID.randomUUID()))
			.setParameter(2, bytes(cart.getId()))
			.setParameter(3, bytes(bookId))
			.executeUpdate());

		assertThat(DbConstraints.isViolated(thrown, "uk_cart_items_cart_book")).isTrue();
	}

	@Test
	void quantityOutsideCheckThroughJpaIsDataIntegrityViolation() {
		Cart cart = Cart.openFor(userId, T1);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, T1);
		carts.saveAndFlush(cart);

		assertThatThrownBy(() -> item.changeQuantity(0, T1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> item.changeQuantity(100, T1)).isInstanceOf(IllegalArgumentException.class);

		// Domain kontrolünü atlayan bir yazım (ör. ileride hatalı kod) DB'de durdurulur.
		ReflectionTestUtils.setField(item, "quantity", 100);
		Throwable thrown = catchThrowable(carts::flush);

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.isViolated(thrown, "ck_cart_items_quantity")).isTrue();
	}

	@Test
	void priceIsStoredWithScaleTwo() {
		Cart cart = Cart.openFor(userId, T1);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, new BigDecimal("149.9"), null, "A", null, T1);

		carts.saveAndFlush(cart);

		assertThat(item.getUnitPriceSnapshot()).isEqualTo(new BigDecimal("149.90"));
		assertThat(jdbc.queryForObject("SELECT CAST(unit_price_snapshot AS CHAR) FROM cart_items WHERE id = ?",
				String.class, bytes(item.getId())))
			.isEqualTo("149.90");
		assertThatThrownBy(() -> cart.addItem(UUID.randomUUID(), 1, new BigDecimal("149.999"), null, "B", null, T1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void currencyDefaultsToTryAndCoverUrlMayBeNull() {
		Cart cart = Cart.openFor(userId, T1);
		CartItem item = cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, T1);

		carts.saveAndFlush(cart);

		assertThat(jdbc.queryForObject(
				"SELECT CONCAT(currency_snapshot, '|', cover_url_snapshot IS NULL) FROM cart_items WHERE id = ?",
				String.class, bytes(item.getId())))
			.isEqualTo("TRY|1");
	}

	@Test
	void findActiveByUserIdForUpdateLocksCartRowWithoutLoadingItems() {
		Cart cart = Cart.openFor(userId, T1);
		cart.addItem(UUID.randomUUID(), 1, BigDecimal.TEN, null, "A", null, T1);
		Cart historic = Cart.openFor(userId, T1);
		historic.checkout(T1);
		carts.saveAndFlush(historic);
		carts.saveAndFlush(cart);
		entityManager.clear();

		SqlCapture.start();
		Cart locked = carts.findActiveByUserIdForUpdate(userId).orElseThrow();
		List<String> statements = SqlCapture.stop();

		assertThat(locked.getId()).isEqualTo(cart.getId());
		assertThat(persistenceUnitUtil.isLoaded(locked, "items")).isFalse();
		assertThat(statements).singleElement().satisfies(sql -> assertThat(sql.toLowerCase(Locale.ROOT))
			.contains("from carts")
			.contains(" for update")
			.doesNotContain("cart_items"));
		assertThat(carts.findActiveByUserIdForUpdate(UUID.randomUUID())).isEmpty();
	}

	/**
	 * Flush sırası tuzağı (5. madde): satır aggregate'ten çıkarılıp (orphanRemoval) aynı kitap aynı flush'ta yeniden
	 * eklenince Hibernate yeni satırın INSERT'ini eski satırın DELETE'inden ÖNCE çalıştırır (INSERT → UPDATE → DELETE)
	 * → {@code uk_cart_items_cart_book}. Servis bu yolu kullanmaz: tekrar ekleme satırı günceller (CartTransactions).
	 */
	@Test
	void removingAndReAddingSameBookInOneFlushViolatesUkCartItemsCartBook() {
		UUID bookId = UUID.randomUUID();
		Cart cart = Cart.openFor(userId, T1);
		CartItem old = cart.addItem(bookId, 2, BigDecimal.TEN, null, "Eski", null, T1);
		carts.saveAndFlush(cart);

		cart.removeItem(old.getId(), T2);
		cart.addItem(bookId, 5, new BigDecimal("12.00"), null, "Yeni", null, T2);
		SqlCapture.start();
		Throwable thrown = catchThrowable(carts::flush);
		List<String> statements = SqlCapture.stop().stream().map(sql -> sql.toLowerCase(Locale.ROOT)).toList();

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbConstraints.isViolated(thrown, "uk_cart_items_cart_book")).isTrue();
		assertThat(statements).singleElement().satisfies(sql -> assertThat(sql).startsWith("insert into cart_items"));
	}

	/** Aynı senaryoda silmeden sonra flush edilirse DELETE önce çalışır; yeni satır tek satır olarak kalır. */
	@Test
	void removingThenFlushingThenReAddingSameBookSucceeds() {
		UUID bookId = UUID.randomUUID();
		Cart cart = Cart.openFor(userId, T1);
		CartItem old = cart.addItem(bookId, 2, BigDecimal.TEN, null, "Eski", null, T1);
		carts.saveAndFlush(cart);

		cart.removeItem(old.getId(), T2);
		carts.flush();
		CartItem fresh = cart.addItem(bookId, 5, new BigDecimal("12.00"), null, "Yeni", null, T2);
		carts.flush();

		assertThat(itemIds(cart.getId())).containsExactly(fresh.getId().toString());
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id = ?", Integer.class,
				bytes(fresh.getId())))
			.isEqualTo(5);
	}

	private List<String> itemIds(UUID cartId) {
		return jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM cart_items WHERE cart_id = ?", String.class,
				bytes(cartId));
	}

	private String dbTime(String table, String column, UUID id) {
		return jdbc.queryForObject("SELECT DATE_FORMAT(" + column + ", '%Y-%m-%d %H:%i:%s.%f') FROM " + table
				+ " WHERE id = ?", String.class, bytes(id));
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16)
			.putLong(id.getMostSignificantBits())
			.putLong(id.getLeastSignificantBits())
			.array();
	}

}
