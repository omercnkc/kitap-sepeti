package com.kitapsepeti.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	boolean existsByUserId(UUID userId);

	/** Liste sırası: varsayılan önce, sonra en yeni. id (UUID v7) aynı anda eklenenlerde sırayı sabitler. */
	@Query("select a from Address a where a.user.id = :userId order by a.isDefault desc, a.createdAt desc, a.id desc")
	List<Address> findAllOrdered(@Param("userId") UUID userId);

	/** Varsayılan silinince yerine geçecek adres. */
	Optional<Address> findFirstByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

	/**
	 * Mevcut varsayılanı kaldırır. Yeni varsayılan yazılmadan ÖNCE çağrılmalı: Hibernate flush'ta
	 * insert'leri update'lerden önce çalıştırdığı için bu iş entity setter'ıyla yapılırsa, yeni varsayılan
	 * eskisi temizlenmeden yazılır ve {@code uk_addresses_default_owner} ihlal edilir.
	 */
	@Modifying
	@Query("update Address a set a.isDefault = false where a.user.id = :userId and a.isDefault = true")
	int clearDefault(@Param("userId") UUID userId);

}
