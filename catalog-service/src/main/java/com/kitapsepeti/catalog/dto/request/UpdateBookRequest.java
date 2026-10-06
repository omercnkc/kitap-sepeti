package com.kitapsepeti.catalog.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kitapsepeti.catalog.validation.HttpUrl;
import com.kitapsepeti.catalog.validation.Isbn;
import com.kitapsepeti.catalog.validation.Isbns;
import com.kitapsepeti.catalog.validation.NullOrNotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Kısmi güncelleme. {@code version} zorunlu: kaydın güncel versiyonu değilse 409 ve hiçbir şey yazılmaz.
 * null alan değiştirilmez; {@code isbn}/{@code description}/{@code coverUrl} için boş metin = temizle.
 * {@code publisherName}/{@code authorNames}/{@code categoryIds} gönderilirse ilgili küme/alan değişir
 * (yayınevi/yazar find-or-create; mevcut kayıt global rename edilmez). Durum ve stok burada değişmez.
 */
public record UpdateBookRequest(
		@Schema(description = "Son okunan `version`; güncel değilse 409 `CONCURRENT_MODIFICATION`") @NotNull Long version,
		@NullOrNotBlank @Size(max = 300) String title,
		@Isbn String isbn,
		@Size(max = BookTexts.MAX_DESCRIPTION_LENGTH) String description,
		@NullOrNotBlank @Size(max = 160) String publisherName,
		@Positive Integer pageCount,
		@HttpUrl @Size(max = 500) String coverUrl,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal priceAmount,
		@Size(max = 20) List<@NotBlank @Size(max = 160) String> authorNames,
		@Size(max = 20) Set<@NotNull UUID> categoryIds) {

	public UpdateBookRequest {
		title = (title != null) ? title.strip() : null;
		isbn = Isbns.normalize(isbn);
		if (publisherName != null) {
			publisherName = publisherName.strip();
		}
		if (authorNames != null) {
			authorNames = AuthorNames.compact(authorNames);
		}
	}

}
