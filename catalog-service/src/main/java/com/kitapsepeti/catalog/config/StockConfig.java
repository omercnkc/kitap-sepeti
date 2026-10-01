package com.kitapsepeti.catalog.config;

import com.kitapsepeti.catalog.service.StockProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StockProperties.class)
public class StockConfig {
}
