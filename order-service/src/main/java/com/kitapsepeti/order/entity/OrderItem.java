package com.kitapsepeti.order.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * Sipariş kalemi ({@code order_items}): checkout anındaki sepet satırının kopyası. Yalnızca {@link Order#place} ile
 * oluşur ve değişmez (Hibernate UPDATE yazmaz). Siparişte kitap başına tek kalem ({@code uk_order_items_order_book}).
 */
@Entity
@Immutable
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	private Order order;

	/** catalog-service'teki kitap; servisler arası olduğu için FK yok. */
	@Column(name = "book_id", nullable = false, updatable = false)
	private UUID bookId;

	@Column(name = "title_snapshot", nullable = false, updatable = false, length = Order.TITLE_MAX_LENGTH)
	private String titleSnapshot;

	@Column(name = "quantity", nullable = false, updatable = false)
	private int quantity;

	/** Scale 2, negatif değil ({@code ck_order_items_unit_price}); 0 = ücretsiz kitap. */
	@Column(name = "unit_price", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	/** {@code unitPrice × quantity}, scale 2 ({@code ck_order_items_line_total}). */
	@Column(name = "line_total", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal lineTotal;

	OrderItem(Order order, UUID bookId, String titleSnapshot, int quantity, BigDecimal unitPrice) {
		this.order = order;
		this.bookId = bookId;
		this.titleSnapshot = titleSnapshot;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

}
