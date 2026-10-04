package com.kitapsepeti.order;

import com.kitapsepeti.common.outbox.OutboxEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration: kimlik yalnızca JWT ile gelir; varsayılan in-memory kullanıcı ve
// "Using generated security password" logu istenmiyor.
// AutoConfigurationPackage: ortak outbox entity'si ve repository'si JPA taramasına açıkça eklenir. Doğrudan konan
// anotasyon @SpringBootApplication'ın varsayılan paketinin yerine geçer; servisin kendi paketi de bu yüzden listede.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@AutoConfigurationPackage(basePackageClasses = { OrderServiceApplication.class, OutboxEvent.class })
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
