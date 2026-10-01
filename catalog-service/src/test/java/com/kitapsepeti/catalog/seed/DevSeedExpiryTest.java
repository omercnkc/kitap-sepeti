package com.kitapsepeti.catalog.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Instant;

import javax.sql.DataSource;

import com.kitapsepeti.catalog.ApiTestSupport;
import com.kitapsepeti.catalog.service.ReservationExpiryJob;
import com.kitapsepeti.catalog.service.StockProperties;
import com.kitapsepeti.catalog.service.StockReservationTransactions;
import com.kitapsepeti.catalog.support.StockInvariant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Seed'in 'held' rezervasyonları (2099'a kadar geçerli) süre dolumu görevine aday olmaz. */
class DevSeedExpiryTest extends ApiTestSupport {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private StockReservationTransactions transactions;

	@Autowired
	private StockProperties properties;

	@Test
	void seedAppliedTwiceKeepsInvariantAndJobDoesNotReleaseSeedReservations() throws Exception {
		runSeed();
		runSeed();
		StockInvariant.assertHolds(jdbc);
		ReservationExpiryJob job = new ReservationExpiryJob(transactions, properties, clock);

		assertThat(job.releaseExpired()).isEqualTo(new ReservationExpiryJob.Result(0, 0));
		clock.fixAt(Instant.parse("2099-12-31T00:00:00Z"));
		assertThat(job.releaseExpired()).as("expires_at < now is strict").isEqualTo(new ReservationExpiryJob.Result(0, 0));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_reservations WHERE status = 'held'", Integer.class))
			.isEqualTo(4);
		StockInvariant.assertHolds(jdbc);
	}

	private void runSeed() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed/R__dev_seed_catalog.sql"));
		}
	}

}
