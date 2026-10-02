package com.kitapsepeti.cart.config;

import com.kitapsepeti.cart.service.CartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CartProperties.class)
public class CartConfig {
}
