package com.kitapsepeti.catalog.dto.request;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.validation.HttpUrl;
import com.kitapsepeti.catalog.validation.Isbn;
import com.kitapsepeti.catalog.validation.Isbns;
import com.kitapsepeti.catalog.validation.NullOrNotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Kısmi güncelleme. {@code version} zorunlu: kaydın güncel versiyonu değilse 409 ve hiçbir şey yazılmaz.
 * null alan değiştirilmez; {@code isbn}/{@code description}/{@code coverUrl} için boş metin = temizle.
 * {@code authorIds}/{@code categoryIds} gönderilirse kümenin tamamını değiştirir. Durum ve stok burada
 * değişmez (ayrı uçlar); bilinmeyen alanlar yok sayılır.
 */
public record UpdateBookRequest(
		@NotNull Long version,
		@NullOrNotBlank @Size(max = 300) String title,
		@Isbn String isbn,
		@Size(max = BookTexts.MAX_DESCRIPTION_LENGTH) String description,
		UUID publisherId,
		@Positive Integer pageCount,
		@HttpUrl @Size(max = 500) String coverUrl,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal priceAmount,
		@Size(max = 20) Set<@NotNull UUID> authorIds,
		@Size(max = 20) Set<@NotNull UUID> categoryIds) {

	public UpdateBookRequest {
		title = (title != null) ? title.strip() : null;
		isbn = Isbns.normalize(isbn);
	}

}
