package com.kitapsepeti.catalog.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.kitapsepeti.catalog.support.StockInvariant;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * local profilindeki örnek veri (db/seed). Spring context'siz; uygulamanın test veritabanından ayrı bir MySQL'de
 * Flyway, local profiliyle aynı konumlarla çalıştırılır. Seed'in idempotent olduğu ayrıca dosyanın doğrudan
 * yeniden çalıştırılmasıyla doğrulanır (Flyway aynı checksum'lı repeatable migration'ı ikinci kez çalıştırmaz).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DevSeedMigrationTest {

	private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@BeforeAll
	static void start() {
		MYSQL.start();
	}

	@AfterAll
	static void stop() {
		MYSQL.stop();
	}

	@Test
	@Order(1)
	void seedAppliesTwiceWithoutErrorsOrDuplicates() throws Exception {
		MigrateResult first = flyway().migrate();
		assertThat(first.success).isTrue();
		assertThat(first.migrations).extracting(migration -> migration.description)
			.contains("create catalog tables", "dev seed catalog");
		long booksAfterFirst = count("SELECT COUNT(*) FROM books");

		MigrateResult second = flyway().migrate();
		assertThat(second.success).isTrue();
		assertThat(second.migrationsExecuted).isZero();
		assertThat(count("SELECT COUNT(*) FROM books")).isEqualTo(booksAfterFirst);

		try (Connection connection = connection()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed/R__dev_seed_catalog.sql"));
		}

		assertThat(count("SELECT COUNT(*) FROM books")).isEqualTo(booksAfterFirst).isEqualTo(14);
		assertThat(count("SELECT COUNT(*) FROM publishers")).isEqualTo(3);
		assertThat(count("SELECT COUNT(*) FROM authors")).isEqualTo(5);
		assertThat(count("SELECT COUNT(*) FROM categories")).isEqualTo(6);
		assertThat(count("SELECT COUNT(*) FROM book_authors")).isEqualTo(14);
		assertThat(count("SELECT COUNT(*) FROM book_categories")).isEqualTo(17);
		assertThat(count("SELECT COUNT(*) FROM books WHERE status = 'published'")).isEqualTo(11);
		assertThat(count("SELECT COUNT(*) FROM books WHERE status = 'draft'")).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM books WHERE status = 'archived'")).isEqualTo(1);
		assertThat(count("""
				SELECT COUNT(*) FROM books
				WHERE status = 'published' AND stock_quantity - reserved_quantity <= 0""")).isEqualTo(2);
		assertThat(count("""
				SELECT COUNT(*) FROM books
				WHERE status = 'published' AND stock_quantity > 0 AND stock_quantity = reserved_quantity""")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM books WHERE title = 'Pride and Prejudice'"))
			.as("Open Library seed title present").isEqualTo(1);
		assertThat(count("""
				SELECT COUNT(*) FROM books
				WHERE cover_url LIKE 'https://covers.openlibrary.org/%'"""))
			.as("all seed books carry Open Library covers").isEqualTo(14);
		assertThat(count("SELECT COUNT(*) FROM books WHERE description LIKE '%klasik%'"))
			.as("Turkish characters survive the round trip").isGreaterThanOrEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM stock_reservations")).isEqualTo(4);
		assertThat(count("SELECT COUNT(DISTINCT order_id) FROM stock_reservations WHERE status = 'held'")).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM stock_reservations WHERE expires_at <> '2099-12-31 00:00:00'"))
			.isZero();
		assertInvariantHolds();
	}

	@Test
	@Order(2)
	void rerunRestoresBooksAndReservationsTogether() throws Exception {
		assertThat(flyway().migrate().success).isTrue();
		// Sipariş 601 iptal edilmiş gibi: satırlar released, rezerv geri verilmiş.
		execute("""
				UPDATE stock_reservations SET status = 'released'
				WHERE order_id = UUID_TO_BIN('01920000-0000-7000-8000-000000000601')""");
		execute("UPDATE books SET reserved_quantity = 1 WHERE id = UUID_TO_BIN('01920000-0000-7000-8000-000000000402')");
		execute("UPDATE books SET reserved_quantity = 0 WHERE id = UUID_TO_BIN('01920000-0000-7000-8000-000000000404')");
		assertInvariantHolds();

		try (Connection connection = connection()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed/R__dev_seed_catalog.sql"));
		}

		assertThat(count("SELECT COUNT(*) FROM stock_reservations WHERE status = 'held'")).isEqualTo(4);
		assertThat(count("""
				SELECT reserved_quantity FROM books
				WHERE id = UUID_TO_BIN('01920000-0000-7000-8000-000000000402')""")).isEqualTo(3);
		assertInvariantHolds();
	}

	@Test
	@Order(3)
	void defaultProfileStartsOnDatabaseThatWasSeededInLocalProfile() {
		assertThat(flyway().migrate().success).isTrue();

		Flyway defaultProfile = Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.locations("classpath:db/migration")
			.ignoreMigrationPatterns(defaultIgnoreMigrationPatterns())
			.load();

		assertThat(defaultProfile.validateWithResult().validationSuccessful).isTrue();
		assertThat(defaultProfile.migrate().migrationsExecuted).isZero();
	}

	private static String[] defaultIgnoreMigrationPatterns() {
		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		yaml.setResources(new ClassPathResource("application.yml"));
		String patterns = yaml.getObject().getProperty("spring.flyway.ignore-migration-patterns");
		assertThat(patterns).as("application.yml spring.flyway.ignore-migration-patterns").isNotBlank();
		return patterns.split(",");
	}

	private static Flyway flyway() {
		return Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.locations("classpath:db/migration", "classpath:db/seed")
			.load();
	}

	private static Connection connection() throws SQLException {
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}

	private static void assertInvariantHolds() throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet violations = statement.executeQuery(StockInvariant.VIOLATIONS_SQL)) {
			List<String> books = new ArrayList<>();
			while (violations.next()) {
				books.add(violations.getString("book_id") + " reserved=" + violations.getInt("reserved_quantity")
						+ " held=" + violations.getInt("held_quantity"));
			}
			assertThat(books).as("books whose reserved_quantity differs from held reservations").isEmpty();
		}
	}

	private static void execute(String sql) throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

	private static long count(String sql) throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getLong(1);
		}
	}

}
