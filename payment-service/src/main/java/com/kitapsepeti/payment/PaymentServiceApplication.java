package com.kitapsepeti.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration: kimlik yalnızca internal API anahtarıyla gelir; varsayılan in-memory kullanıcı
// ve "Using generated security password" logu istenmiyor.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
