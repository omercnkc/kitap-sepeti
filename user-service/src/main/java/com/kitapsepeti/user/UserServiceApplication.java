package com.kitapsepeti.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration: giriş AuthService'te elle doğrulanacak; varsayılan
// in-memory kullanıcı ve "Using generated security password" logu istenmiyor.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
