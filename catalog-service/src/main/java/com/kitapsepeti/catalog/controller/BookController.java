package com.kitapsepeti.catalog.controller;

import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.BookSearchRequest;
import com.kitapsepeti.catalog.dto.response.BookDetailResponse;
import com.kitapsepeti.catalog.dto.response.BookSummaryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.BookQueryService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Herkese açık kitap okuma uçları (SecurityConfig: GET /api/books/** permitAll, token yok sayılır). */
@RestController
@RequestMapping(path = "/api/books", produces = MediaType.APPLICATION_JSON_VALUE)
public class BookController {

	private final BookQueryService bookQueryService;

	public BookController(BookQueryService bookQueryService) {
		this.bookQueryService = bookQueryService;
	}

	/** Yayındaki kitaplar; filtre, sıralama ve sayfa query parametrelerinden ({@link BookSearchRequest}). */
	@GetMapping
	public PageResponse<BookSummaryResponse> search(@Valid @ModelAttribute BookSearchRequest request) {
		return bookQueryService.search(request);
	}

	@GetMapping("/{id}")
	public BookDetailResponse get(@PathVariable UUID id) {
		return bookQueryService.getPublished(id);
	}

}
