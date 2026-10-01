package com.kitapsepeti.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import com.kitapsepeti.catalog.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link Book} kayıtlarına erişim; filtreli listeleme {@link JpaSpecificationExecutor} ile yapılır.
 */
public interface BookRepository extends JpaRepository<Book, UUID>, JpaSpecificationExecutor<Book> {

	/**
	 * Kitabı yayınevi, yazarlar ve kategorilerle tek sorguda getirir; dönen nesnenin bu alanlarına
	 * transaction dışında da erişilebilir. İki koleksiyon da {@code Set} olduğu için birlikte fetch edilebilir.
	 */
	@EntityGraph(attributePaths = { "publisher", "authors", "categories" })
	Optional<Book> findWithDetailsById(UUID id);

	/**
	 * Sayfalı liste; yalnızca to-one yayınevi aynı sorguda gelir (sayım sorgusuna graph uygulanmaz).
	 * Koleksiyonlar burada fetch EDİLMEZ: sayfalamayla to-many fetch Hibernate'i bellekte sayfalamaya zorlar.
	 */
	@Override
	@EntityGraph(attributePaths = "publisher")
	Page<Book> findAll(Specification<Book> spec, Pageable pageable);

	/**
	 * Kitap satırı transaction sonuna kadar kilitli ({@code SELECT ... FOR UPDATE}); ilişkiler lazy yüklenir.
	 * Admin durum/içerik değişikliklerinde kullanılır: eşzamanlı stok düzeltmesi bu transaction bitene kadar
	 * bekler, böylece olaydaki {@code inStock} ve durum birbirinin eski haline göre hesaplanmaz.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Book b where b.id = :id")
	Optional<Book> findForUpdateById(@Param("id") UUID id);

	/**
	 * Stoğu tek koşullu UPDATE ile değiştirir; sonuç rezervin altına inecekse satır güncellenmez. Okuma-yazma
	 * arası yarış yoktur (kontrol ve yazma aynı ifadede, satır kilidi altında). Versiyon değişmez: stok, admin
	 * düzenleme formunun parçası değildir. updated_at ise kolonun {@code ON UPDATE CURRENT_TIMESTAMP(6)} tanımıyla
	 * DB saatine güncellenir ({@code @UpdateTimestamp} bulk UPDATE'te çalışmaz; satır güncellenmezse dokunulmaz).
	 * Önce bekleyen değişiklikler flush edilir, sonra persistence context temizlenir (bayat stok okunmasın).
	 * @return etkilenen satır sayısı: 1 başarılı; 0 kitap yok veya stok rezervin altına inerdi
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Book b set b.stockQuantity = b.stockQuantity + :delta "
			+ "where b.id = :id and b.stockQuantity + :delta >= b.reservedQuantity")
	int adjustStock(@Param("id") UUID id, @Param("delta") int delta);

}
