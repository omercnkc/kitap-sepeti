package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.AdminBookListRequest;
import com.kitapsepeti.catalog.dto.request.CreateBookRequest;
import com.kitapsepeti.catalog.dto.request.StockAdjustmentRequest;
import com.kitapsepeti.catalog.dto.request.UpdateBookRequest;
import com.kitapsepeti.catalog.dto.response.AdminBookResponse;
import com.kitapsepeti.catalog.dto.response.AdminBookSummaryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.BookAdminService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kitap yönetimi (SecurityConfig: /api/admin/** yalnızca ADMIN). Tüm durumlar görünür; yanıtlar stok
 * miktarlarını ve versiyonu içerir. Durum ve stok yalnızca kendi uçlarıyla değişir.
 */
@RestController
@RequestMapping(path = "/api/admin/books", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminBookController {

	private static final String BASE_PATH = "/api/admin/books/";

	private final BookAdminService bookAdminService;

	public AdminBookController(BookAdminService bookAdminService) {
		this.bookAdminService = bookAdminService;
	}

	/** Son güncellenen önce ({@code updatedAt desc, id desc}). */
	@GetMapping
	public PageResponse<AdminBookSummaryResponse> list(@Valid @ModelAttribute AdminBookListRequest request) {
		return bookAdminService.list(request);
	}

	@GetMapping("/{id}")
	public AdminBookResponse get(@PathVariable UUID id) {
		return bookAdminService.get(id);
	}

	@PostMapping
	public ResponseEntity<AdminBookResponse> create(@Valid @RequestBody CreateBookRequest request) {
		AdminBookResponse created = bookAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	public AdminBookResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBookRequest request) {
		return bookAdminService.update(id, request);
	}

	@PostMapping("/{id}/publish")
	public AdminBookResponse publish(@PathVariable UUID id) {
		return bookAdminService.publish(id);
	}

	@PostMapping("/{id}/archive")
	public AdminBookResponse archive(@PathVariable UUID id) {
		return bookAdminService.archive(id);
	}

	/** Arşivlemeyle aynı işlem; kayıt silinmez (sipariş geçmişi için korunur). */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		bookAdminService.archive(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/stock-adjustments")
	public AdminBookResponse adjustStock(@PathVariable UUID id,
			@Valid @RequestBody StockAdjustmentRequest request) {
		return bookAdminService.adjustStock(id, request);
	}

}
