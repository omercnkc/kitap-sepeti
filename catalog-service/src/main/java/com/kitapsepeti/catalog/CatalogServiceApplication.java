package com.kitapsepeti.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration: kimlik yalnızca JWT ile gelir; varsayılan in-memory kullanıcı ve
// "Using generated security password" logu istenmiyor.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CatalogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CatalogServiceApplication.class, args);
	}

}
