package com.kitapsepeti.order.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Şimdilik zamanlanmış tek iş outbox worker'ı; o kapalıyken scheduler da açılmaz. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class SchedulingConfig {

}
