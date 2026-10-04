package com.kitapsepeti.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Zamanlanmış işler: şimdilik yalnızca outbox worker. Kapalıyken scheduler açılmaz. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class SchedulingConfig {

}
