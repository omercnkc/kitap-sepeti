package com.kitapsepeti.catalog.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.validation.HttpUrl;
import com.kitapsepeti.catalog.validation.Isbn;
import com.kitapsepeti.catalog.validation.Isbns;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Yeni kitap (her zaman DRAFT, para birimi her zaman TRY). Opsiyonel metin alanlarında boş değer "yok" sayılır.
 * ISBN normalize edilmiş haliyle doğrulanır ve saklanır. Yazar adları sunucuda find-or-create;
 * kategori id varlığı serviste kontrol edilir.
 */
public record CreateBookRequest(
		@NotBlank @Size(max = 300) String title,
		@Isbn String isbn,
		@Size(max = BookTexts.MAX_DESCRIPTION_LENGTH) String description,
		@Positive Integer pageCount,
		@HttpUrl @Size(max = 500) String coverUrl,
		@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal priceAmount,
		@PositiveOrZero @Max(1_000_000) Integer initialStock,
		@Size(max = 20) List<@NotBlank @Size(max = 160) String> authorNames,
		@Size(max = 20) Set<@NotNull UUID> categoryIds) {

	public CreateBookRequest {
		title = (title != null) ? title.strip() : null;
		isbn = BookTexts.blankToNull(Isbns.normalize(isbn));
		description = BookTexts.blankToNull(description);
		coverUrl = BookTexts.blankToNull(coverUrl);
		initialStock = (initialStock != null) ? initialStock : 0;
		authorNames = AuthorNames.compact(authorNames);
		categoryIds = (categoryIds != null) ? categoryIds : Set.of();
	}

}
