package com.kitapsepeti.user.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uygulama saati. Zaman hesabı yapan sınıflar {@code Instant.now()} yerine bunu kullanır;
 * farklı saat isteyen testler kendi test konfigürasyonunda bu bean'i override eder.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
