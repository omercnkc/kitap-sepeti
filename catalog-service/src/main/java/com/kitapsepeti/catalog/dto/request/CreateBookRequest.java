package com.kitapsepeti.catalog.dto.request;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.validation.HttpUrl;
import com.kitapsepeti.catalog.validation.Isbn;
import com.kitapsepeti.catalog.validation.Isbns;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Yeni kitap (her zaman DRAFT, para birimi her zaman TRY). Opsiyonel metin alanlarında boş değer "yok" sayılır.
 * ISBN normalize edilmiş haliyle doğrulanır ve saklanır. Yayınevi/yazar/kategori varlığı serviste kontrol edilir.
 */
public record CreateBookRequest(
		@NotBlank @Size(max = 300) String title,
		@Isbn String isbn,
		@Size(max = BookTexts.MAX_DESCRIPTION_LENGTH) String description,
		@NotNull UUID publisherId,
		@Positive Integer pageCount,
		@HttpUrl @Size(max = 500) String coverUrl,
		@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal priceAmount,
		@PositiveOrZero Integer initialStock,
		@Size(max = 20) Set<@NotNull UUID> authorIds,
		@Size(max = 20) Set<@NotNull UUID> categoryIds) {

	public CreateBookRequest {
		title = (title != null) ? title.strip() : null;
		isbn = BookTexts.blankToNull(Isbns.normalize(isbn));
		description = BookTexts.blankToNull(description);
		coverUrl = BookTexts.blankToNull(coverUrl);
		initialStock = (initialStock != null) ? initialStock : 0;
		authorIds = (authorIds != null) ? authorIds : Set.of();
		categoryIds = (categoryIds != null) ? categoryIds : Set.of();
	}

}
