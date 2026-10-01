package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.config.OpenApiConfig;
import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreateAuthorRequest;
import com.kitapsepeti.catalog.dto.request.UpdateAuthorRequest;
import com.kitapsepeti.catalog.dto.response.AuthorResponse;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.service.AuthorAdminService;
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

/** Yazar yönetimi (SecurityConfig: /api/admin/** yalnızca ADMIN). */
@RestController
@RequestMapping(path = "/api/admin/authors", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_ADMIN_AUTHORS, description = "Yazar yönetimi.")
public class AdminAuthorController {

	private static final String BASE_PATH = "/api/admin/authors/";

	private final AuthorAdminService authorAdminService;

	public AdminAuthorController(AuthorAdminService authorAdminService) {
		this.authorAdminService = authorAdminService;
	}

	@GetMapping
	@Operation(operationId = "listAuthors", summary = "Yazarları listele", description = "Ada göre sıralı.")
	@ApiResponse(responseCode = "200", description = "Sayfa.")
	public PageResponse<AuthorResponse> list(@Valid @ParameterObject @ModelAttribute AdminPageRequest page) {
		return authorAdminService.list(page);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getAuthor", summary = "Yazar getir")
	@ApiResponse(responseCode = "200", description = "Yazar.")
	public AuthorResponse get(@Parameter(description = "Yazar id'si") @PathVariable UUID id) {
		return authorAdminService.get(id);
	}

	@PostMapping
	@Operation(operationId = "createAuthor", summary = "Yazar oluştur",
			description = "`slug` verilmezse addan üretilir (Türkçe karakterler sadeleştirilir).")
	@ApiResponse(responseCode = "201", description = "Oluşturuldu.", headers = @Header(name = "Location",
			description = "Yeni yazarın yolu.", schema = @Schema(type = "string", format = "uri-reference")))
	@ApiResponse(responseCode = "409", description = "`SLUG_ALREADY_EXISTS`: slug kullanımda.")
	public ResponseEntity<AuthorResponse> create(@Valid @RequestBody CreateAuthorRequest request) {
		AuthorResponse created = authorAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	@Operation(operationId = "updateAuthor", summary = "Yazar güncelle",
			description = "Kısmi güncelleme; yalnızca ad değişirse slug korunur.")
	@ApiResponse(responseCode = "200", description = "Güncellenmiş yazar.")
	@ApiResponse(responseCode = "409", description = "`SLUG_ALREADY_EXISTS`: slug kullanımda.")
	public AuthorResponse update(@Parameter(description = "Yazar id'si") @PathVariable UUID id,
			@Valid @RequestBody UpdateAuthorRequest request) {
		return authorAdminService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(operationId = "deleteAuthor", summary = "Yazar sil")
	@ApiResponse(responseCode = "204", description = "Silindi.")
	@ApiResponse(responseCode = "409", description = "`RESOURCE_IN_USE`: yazara bağlı kitap var.")
	public ResponseEntity<Void> delete(@Parameter(description = "Yazar id'si") @PathVariable UUID id) {
		authorAdminService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
