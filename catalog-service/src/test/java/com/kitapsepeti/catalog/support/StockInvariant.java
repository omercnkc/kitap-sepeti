package com.kitapsepeti.catalog.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stok değişmezi: her kitapta {@code reserved_quantity = SUM(quantity WHERE status = 'held')}. Sorgu yalnızca
 * tutarsız kitapları döndürür (rezervasyonu olmayan kitapta toplam 0 sayılır).
 */
public final class StockInvariant {

	public static final String VIOLATIONS_SQL = """
			SELECT BIN_TO_UUID(b.id) AS book_id, b.reserved_quantity, COALESCE(SUM(r.quantity), 0) AS held_quantity
			FROM books b
			LEFT JOIN stock_reservations r ON r.book_id = b.id AND r.status = 'held'
			GROUP BY b.id, b.reserved_quantity
			HAVING b.reserved_quantity <> COALESCE(SUM(r.quantity), 0)""";

	private StockInvariant() {
	}

	public static void assertHolds(JdbcTemplate jdbc) {
		List<Map<String, Object>> violations = jdbc.queryForList(VIOLATIONS_SQL);
		assertThat(violations).as("books whose reserved_quantity differs from held reservations").isEmpty();
	}

}
