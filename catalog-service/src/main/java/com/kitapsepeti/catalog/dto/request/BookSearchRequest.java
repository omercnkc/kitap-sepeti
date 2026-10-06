package com.kitapsepeti.catalog.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import com.kitapsepeti.catalog.validation.ValidPriceRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * {@code GET /api/books} query parametreleri; hepsi opsiyonel. Verilmeyenler compact constructor'da varsayılana
 * çekilir (sort NEWEST, page 0, size 20) — Spring eksik parametrede ilkel tipe 0 koyacağı için sarmalayıcı tipler.
 * Tip dönüşümü hatası (bozuk UUID/sayı, tanınmayan sort) ve kural ihlali 400 VALIDATION_FAILED olur.
 *
 * @param categoryId kategori; alt kategorilerdeki kitaplar da dahil
 */
@ValidPriceRange
public record BookSearchRequest(
		@Schema(description = "Kategori; alt kategorilerdeki kitaplar da dahil") UUID categoryId,
		UUID authorId,
		@DecimalMin("0") BigDecimal minPrice,
		@Schema(description = "`minPrice` ile birlikte verilirse ondan küçük olamaz") @DecimalMin("0") BigDecimal maxPrice,
		@Schema(type = "string", allowableValues = { "newest", "price_asc", "price_desc", "title_asc" },
				defaultValue = "newest", description = "Büyük/küçük harf duyarsız") BookSort sort,
		@Schema(defaultValue = "0") @Min(0) Integer page,
		@Schema(defaultValue = "20") @Min(1) @Max(50) Integer size) {

	public static final int DEFAULT_SIZE = 20;

	public BookSearchRequest {
		sort = (sort != null) ? sort : BookSort.NEWEST;
		page = (page != null) ? page : 0;
		size = (size != null) ? size : DEFAULT_SIZE;
	}

}
