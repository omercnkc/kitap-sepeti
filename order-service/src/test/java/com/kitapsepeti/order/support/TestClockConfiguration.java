package com.kitapsepeti.order.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Uygulama saatinin yerine {@link MutableClock}; varsayılan farkı sıfır olduğu için diğer testleri etkilemez. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

}
