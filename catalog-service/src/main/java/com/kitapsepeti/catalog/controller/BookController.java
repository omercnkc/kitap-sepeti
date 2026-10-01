package com.kitapsepeti.catalog.controller;

import java.util.UUID;

import com.kitapsepeti.catalog.config.OpenApiConfig;
import com.kitapsepeti.catalog.dto.request.BookSearchRequest;
import com.kitapsepeti.catalog.dto.response.BookDetailResponse;
import com.kitapsepeti.catalog.dto.response.BookSummaryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.BookQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Herkese açık kitap okuma uçları (SecurityConfig: GET /api/books/** permitAll, token yok sayılır). */
@RestController
@RequestMapping(path = "/api/books", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_BOOKS, description = "Yayındaki kitaplar. Kimlik gerekmez; stok miktarı değil "
		+ "yalnızca `inStock` döner.")
public class BookController {

	private final BookQueryService bookQueryService;

	public BookController(BookQueryService bookQueryService) {
		this.bookQueryService = bookQueryService;
	}

	/** Yayındaki kitaplar; filtre, sıralama ve sayfa query parametrelerinden ({@link BookSearchRequest}). */
	@GetMapping
	@Operation(operationId = "searchBooks", summary = "Kitapları listele / ara",
			description = "Yalnızca yayındaki kitaplar. `categoryId` alt kategorilerdeki kitapları da kapsar. "
					+ "`minPrice` > `maxPrice` ise 400.")
	@ApiResponse(responseCode = "200", description = "Sayfa (son sayfadan sonrası boş `items` döner).")
	public PageResponse<BookSummaryResponse> search(@Valid @ParameterObject @ModelAttribute BookSearchRequest request) {
		return bookQueryService.search(request);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getBook", summary = "Kitap detayı")
	@ApiResponse(responseCode = "200", description = "Kitap.")
	@ApiResponse(responseCode = "404", description = "`RESOURCE_NOT_FOUND`: kitap yok ya da yayında değil "
			+ "(taslak/arşiv).")
	public BookDetailResponse get(@Parameter(description = "Kitap id'si") @PathVariable UUID id) {
		return bookQueryService.getPublished(id);
	}

}
