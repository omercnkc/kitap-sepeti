package com.kitapsepeti.catalog.dto.request;

import com.kitapsepeti.catalog.entity.BookStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Admin kitap listesi query parametreleri. {@code status} opsiyonel (draft|published|archived, büyük/küçük harf
 * duyarsız; tanınmayan değer 400); yoksa tüm durumlar listelenir.
 */
public record AdminBookListRequest(BookStatus status, @Min(0) Integer page, @Min(1) @Max(100) Integer size) {

	public AdminBookListRequest {
		page = (page != null) ? page : 0;
		size = (size != null) ? size : AdminPageRequest.DEFAULT_SIZE;
	}

}
