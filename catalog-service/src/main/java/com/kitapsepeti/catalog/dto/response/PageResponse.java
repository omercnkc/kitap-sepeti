package com.kitapsepeti.catalog.dto.response;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/**
 * Sayfalı liste. {@code page} 0'dan başlar; son sayfadan sonrası istenirse {@code items} boş, toplamlar yine doğrudur.
 * Spring Data'nın {@link Page} JSON'u kararlı bir sözleşme olmadığı için dışarıya bu yapı verilir.
 */
public record PageResponse<T>(
		@Schema(requiredMode = REQUIRED) List<T> items,
		@Schema(requiredMode = REQUIRED) int page,
		@Schema(requiredMode = REQUIRED) int size,
		@Schema(requiredMode = REQUIRED) long totalElements,
		@Schema(requiredMode = REQUIRED) int totalPages) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

}
