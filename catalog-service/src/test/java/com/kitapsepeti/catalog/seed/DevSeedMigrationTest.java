package com.kitapsepeti.catalog.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
		assertThat(count("SELECT COUNT(*) FROM book_authors")).isEqualTo(18);
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
		assertThat(count("SELECT COUNT(*) FROM books WHERE title = 'Sessiz Ağaçların Şarkısı'"))
			.as("Turkish characters survive the round trip").isEqualTo(1);
	}

	@Test
	@Order(2)
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

	private static long count(String sql) throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getLong(1);
		}
	}

}
