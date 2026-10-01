package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.config.OpenApiConfig;
import com.kitapsepeti.catalog.dto.request.AdminBookListRequest;
import com.kitapsepeti.catalog.dto.request.CreateBookRequest;
import com.kitapsepeti.catalog.dto.request.StockAdjustmentRequest;
import com.kitapsepeti.catalog.dto.request.UpdateBookRequest;
import com.kitapsepeti.catalog.dto.response.AdminBookResponse;
import com.kitapsepeti.catalog.dto.response.AdminBookSummaryResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.BookAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = OpenApiConfig.TAG_ADMIN_BOOKS, description = "Kitap yönetimi: tüm durumlar, stok miktarları ve "
		+ "versiyon görünür. Durum ve stok yalnızca kendi uçlarıyla değişir; kitap fiziksel olarak silinmez.")
public class AdminBookController {

	private static final String BASE_PATH = "/api/admin/books/";

	private final BookAdminService bookAdminService;

	public AdminBookController(BookAdminService bookAdminService) {
		this.bookAdminService = bookAdminService;
	}

	/** Son güncellenen önce ({@code updatedAt desc, id desc}). */
	@GetMapping
	@Operation(operationId = "listAdminBooks", summary = "Kitapları listele (tüm durumlar)",
			description = "Son güncellenen önce. `status` verilmezse tüm durumlar.")
	@ApiResponse(responseCode = "200", description = "Sayfa.")
	public PageResponse<AdminBookSummaryResponse> list(@Valid @ParameterObject @ModelAttribute AdminBookListRequest request) {
		return bookAdminService.list(request);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getAdminBook", summary = "Kitap getir (tüm durumlar)")
	@ApiResponse(responseCode = "200", description = "Kitap.")
	public AdminBookResponse get(@Parameter(description = "Kitap id'si") @PathVariable UUID id) {
		return bookAdminService.get(id);
	}

	@PostMapping
	@Operation(operationId = "createBook", summary = "Kitap oluştur",
			description = "Kitap her zaman `draft` ve `TRY` olarak oluşur; olay yazılmaz. Olmayan yayınevi/yazar/"
					+ "kategori → 400 `VALIDATION_FAILED` (ilgili alan `errors`'ta).")
	@ApiResponse(responseCode = "201", description = "Oluşturuldu.", headers = @Header(name = "Location",
			description = "Yeni kitabın yolu.", schema = @Schema(type = "string", format = "uri-reference")))
	@ApiResponse(responseCode = "409", description = "`ISBN_ALREADY_EXISTS`: ISBN başka bir kitapta kayıtlı.")
	public ResponseEntity<AdminBookResponse> create(@Valid @RequestBody CreateBookRequest request) {
		AdminBookResponse created = bookAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	@Operation(operationId = "updateBook", summary = "Kitap güncelle",
			description = "Kısmi güncelleme; `version` zorunlu ve kaydın güncel versiyonu olmalı. Yayındaki kitapta "
					+ "`BookUpserted` olayı yazılır.")
	@ApiResponse(responseCode = "200", description = "Güncellenmiş kitap.")
	@ApiResponse(responseCode = "409", description = "`CONCURRENT_MODIFICATION`: `version` güncel değil (hiçbir şey "
			+ "yazılmadı; kaydı yeniden okuyun) veya `ISBN_ALREADY_EXISTS`.")
	public AdminBookResponse update(@Parameter(description = "Kitap id'si") @PathVariable UUID id,
			@Valid @RequestBody UpdateBookRequest request) {
		return bookAdminService.update(id, request);
	}

	@PostMapping("/{id}/publish")
	@Operation(operationId = "publishBook", summary = "Yayınla",
			description = "`draft`/`archived` → `published`. Zaten yayındaysa değişiklik yok, 200.")
	@ApiResponse(responseCode = "200", description = "Kitap.")
	@ApiResponse(responseCode = "409", description = "`BOOK_NOT_PUBLISHABLE`: en az bir yazar, en az bir kategori ve "
			+ "sıfırdan büyük fiyat gerekir.")
	public AdminBookResponse publish(@Parameter(description = "Kitap id'si") @PathVariable UUID id) {
		return bookAdminService.publish(id);
	}

	@PostMapping("/{id}/archive")
	@Operation(operationId = "archiveBook", summary = "Arşivle",
			description = "→ `archived` (kayıt silinmez). Yayındaysa `BookRemoved` olayı yazılır. Zaten arşivdeyse 200.")
	@ApiResponse(responseCode = "200", description = "Kitap.")
	public AdminBookResponse archive(@Parameter(description = "Kitap id'si") @PathVariable UUID id) {
		return bookAdminService.archive(id);
	}

	/** Arşivlemeyle aynı işlem; kayıt silinmez (sipariş geçmişi için korunur). */
	@DeleteMapping("/{id}")
	@Operation(operationId = "deleteBook", summary = "Sil (= arşivle)",
			description = "Arşivlemeyle aynı işlem; kayıt fiziksel olarak silinmez.")
	@ApiResponse(responseCode = "204", description = "Arşivlendi.")
	public ResponseEntity<Void> delete(@Parameter(description = "Kitap id'si") @PathVariable UUID id) {
		bookAdminService.archive(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/stock-adjustments")
	@Operation(operationId = "adjustBookStock", summary = "Stok düzelt",
			description = "Stoğa `delta` eklenir (negatifse düşülür); `delta` = 0 → 400. Yayındaki kitabın "
					+ "`inStock` değeri değişirse `BookUpserted` yazılır.")
	@ApiResponse(responseCode = "200", description = "Güncellenmiş kitap.")
	@ApiResponse(responseCode = "409", description = "`STOCK_BELOW_RESERVED`: stok, rezerve miktarın altına inemez.")
	public AdminBookResponse adjustStock(@Parameter(description = "Kitap id'si") @PathVariable UUID id,
			@Valid @RequestBody StockAdjustmentRequest request) {
		return bookAdminService.adjustStock(id, request);
	}

}
