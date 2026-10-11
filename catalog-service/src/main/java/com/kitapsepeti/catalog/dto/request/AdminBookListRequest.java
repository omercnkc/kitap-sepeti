package com.kitapsepeti.catalog.dto.request;

import java.util.UUID;

import com.kitapsepeti.catalog.entity.BookStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Admin kitap listesi query parametreleri. {@code status} opsiyonel (draft|published|archived, büyük/küçük harf
 * duyarsız; tanınmayan değer 400); yoksa tüm durumlar listelenir. {@code q} / {@code authorId} opsiyonel filtreler.
 */
public record AdminBookListRequest(
		@Schema(description = "Başlıkta arama (LIKE); boş veya yalnızca boşluk yok sayılır") @Size(min = 2,
				max = 100) String q,
		@Schema(description = "Yazar id; yalnızca bu yazara bağlı kitaplar") UUID authorId,
		@Schema(type = "string", allowableValues = { "draft", "published", "archived" },
				description = "Büyük/küçük harf duyarsız; verilmezse tüm durumlar") BookStatus status,
		@Schema(defaultValue = "0") @Min(0) Integer page,
		@Schema(defaultValue = "20") @Min(1) @Max(100) Integer size) {

	public AdminBookListRequest {
		q = (q != null && !q.isBlank()) ? q.strip() : null;
		page = (page != null) ? page : 0;
		size = (size != null) ? size : AdminPageRequest.DEFAULT_SIZE;
	}

}
