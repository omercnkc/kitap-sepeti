package com.kitapsepeti.cart;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reuse yalnızca {@code ~/.testcontainers.properties} içinde {@code testcontainers.reuse.enable=true} ise etkilidir; yoksa
 * her bağlam kendi konteynerini açar. Veritabanı adı modüle özgü: reuse hash'i ayarlardan hesaplanır, diğer modüllerin
 * MySQL konteyneriyle (ve Flyway geçmişiyle) çakışmasın.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4")).withDatabaseName("cart_test").withReuse(true);
	}

}
