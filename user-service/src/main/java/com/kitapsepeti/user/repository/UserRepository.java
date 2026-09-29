package com.kitapsepeti.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link User} kayıtlarına erişim. E-posta karşılaştırması DB collation'ı nedeniyle
 * büyük/küçük harf duyarsızdır.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	/** Giriş sırasında kullanıcıyı e-postasıyla bulur. */
	Optional<User> findByEmail(String email);

	/** Kayıt sırasında e-postanın daha önce alınıp alınmadığını kontrol eder. */
	boolean existsByEmail(String email);

}
