package com.kitapsepeti.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link Address} kayıtlarına erişim. Sorgular kullanıcı id'siyle sınırlandırılır ki
 * bir kullanıcı başkasının adresine erişemesin.
 */
public interface AddressRepository extends JpaRepository<Address, UUID> {

	/** Kullanıcının tüm adresleri. */
	List<Address> findAllByUserId(UUID userId);

	/** Adresi yalnızca verilen kullanıcıya aitse döner; başkasının adresi için boş döner. */
	Optional<Address> findByIdAndUserId(UUID id, UUID userId);

	/** Kullanıcının varsayılan adresi (DB kısıtı sayesinde en fazla bir tane). */
	Optional<Address> findByUserIdAndIsDefaultTrue(UUID userId);

}
