package com.kitapsepeti.user.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uygulama saati. Zaman hesabı yapan sınıflar {@code Instant.now()} yerine bunu kullanır;
 * testlerde sabit veya ileri saat verilebilir.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	@ConditionalOnMissingBean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
