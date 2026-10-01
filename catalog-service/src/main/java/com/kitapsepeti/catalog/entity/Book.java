package com.kitapsepeti.catalog.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Kitap ({@code books} tablosu). Yazar ve kategori bağları tek yönlüdür ve yalnızca buradan yönetilir;
 * kitap silinince {@code book_authors} / {@code book_categories} satırları da silinir (DB CASCADE).
 * Satılabilir stok = {@code stockQuantity - reservedQuantity}; DB kısıtları ikisinin de negatif olmamasını
 * ve rezervin stoğu aşmamasını garanti eder.
 */
@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

	/** Uygulama tarafında üretilen, zamana göre sıralı UUID v7; DB'de BINARY(16). */
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Benzersiz; boş olabilir (UNIQUE indeks NULL'ları saymaz). */
	@Setter
	@Column(name = "isbn", length = 20)
	private String isbn;

	@Setter
	@Column(name = "title", nullable = false, length = 300)
	private String title;

	@Setter
	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	/** Kitabı olan yayınevi silinemez (DB RESTRICT). */
	@Setter
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "publisher_id", nullable = false)
	private Publisher publisher;

	/** Null olabilir; doluysa pozitif ({@code ck_books_page_count}). */
	@Setter
	@Column(name = "page_count")
	private Integer pageCount;

	@Setter
	@Column(name = "cover_url", length = 500)
	private String coverUrl;

	/** Negatif olamaz ({@code ck_books_price_non_negative}). */
	@Setter
	@Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal priceAmount;

	/** ISO 4217 para birimi kodu (CHAR(3)). */
	@Setter
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "TRY";

	/**
	 * Yalnızca insert'te yazılır; sonrasında entity üzerinden ASLA güncellenmez (Hibernate UPDATE'ine girmez).
	 * Değişiklik yalnızca koşullu toplu UPDATE ile yapılır ({@code BookRepository#adjustStock}); böylece
	 * eşzamanlı bir admin düzenlemesi stok/rezerv değerini eski haliyle ezemez.
	 */
	@Column(name = "stock_quantity", nullable = false, updatable = false)
	private int stockQuantity = 0;

	/** Aktif ('held') rezervasyonların toplamı; {@code stockQuantity}'yi aşamaz. Stok ile aynı kural. */
	@Column(name = "reserved_quantity", nullable = false, updatable = false)
	private int reservedQuantity = 0;

	/** DB'de küçük harf ('draft'/'published'/'archived'); varsayılan Java'da da verilir, DB default'una güvenilmez. */
	@Setter
	@Convert(converter = BookStatusConverter.class)
	@Column(name = "status", nullable = false, length = 16)
	private BookStatus status = BookStatus.DRAFT;

	@Setter
	@Column(name = "published_at")
	private Instant publishedAt;

	/** Optimistic lock; Hibernate her güncellemede artırır, eski versiyonla güncelleme reddedilir. */
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "book_authors",
			joinColumns = @JoinColumn(name = "book_id"),
			inverseJoinColumns = @JoinColumn(name = "author_id"))
	private Set<Author> authors = new HashSet<>();

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "book_categories",
			joinColumns = @JoinColumn(name = "book_id"),
			inverseJoinColumns = @JoinColumn(name = "category_id"))
	private Set<Category> categories = new HashSet<>();

	/** Zorunlu alanlarla yeni kitap; status=DRAFT, currency="TRY", stok ve rezerv 0 başlar. */
	public Book(String title, Publisher publisher, BigDecimal priceAmount) {
		this(title, publisher, priceAmount, 0);
	}

	/** Başlangıç stoğuyla yeni kitap; rezerv 0 başlar. Stok yalnızca burada (insert) verilebilir. */
	public Book(String title, Publisher publisher, BigDecimal priceAmount, int initialStock) {
		this.title = title;
		this.publisher = publisher;
		this.priceAmount = priceAmount;
		this.stockQuantity = initialStock;
	}

	/** Satılabilir stok = stok - rezerv. */
	public int getAvailableQuantity() {
		return this.stockQuantity - this.reservedQuantity;
	}

}
