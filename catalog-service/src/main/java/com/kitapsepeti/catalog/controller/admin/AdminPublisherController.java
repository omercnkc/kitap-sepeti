package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.config.OpenApiConfig;
import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreatePublisherRequest;
import com.kitapsepeti.catalog.dto.request.UpdatePublisherRequest;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.dto.response.PublisherResponse;
import com.kitapsepeti.catalog.service.PublisherAdminService;
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

/** Yayınevi yönetimi (SecurityConfig: /api/admin/** yalnızca ADMIN). */
@RestController
@RequestMapping(path = "/api/admin/publishers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = OpenApiConfig.TAG_ADMIN_PUBLISHERS, description = "Yayınevi yönetimi.")
public class AdminPublisherController {

	private static final String BASE_PATH = "/api/admin/publishers/";

	private final PublisherAdminService publisherAdminService;

	public AdminPublisherController(PublisherAdminService publisherAdminService) {
		this.publisherAdminService = publisherAdminService;
	}

	@GetMapping
	@Operation(operationId = "listPublishers", summary = "Yayınevlerini listele", description = "Ada göre sıralı.")
	@ApiResponse(responseCode = "200", description = "Sayfa.")
	public PageResponse<PublisherResponse> list(@Valid @ParameterObject @ModelAttribute AdminPageRequest page) {
		return publisherAdminService.list(page);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getPublisher", summary = "Yayınevi getir")
	@ApiResponse(responseCode = "200", description = "Yayınevi.")
	public PublisherResponse get(@Parameter(description = "Yayınevi id'si") @PathVariable UUID id) {
		return publisherAdminService.get(id);
	}

	@PostMapping
	@Operation(operationId = "createPublisher", summary = "Yayınevi oluştur",
			description = "`slug` verilmezse addan üretilir (Türkçe karakterler sadeleştirilir).")
	@ApiResponse(responseCode = "201", description = "Oluşturuldu.", headers = @Header(name = "Location",
			description = "Yeni yayınevinin yolu.", schema = @Schema(type = "string", format = "uri-reference")))
	@ApiResponse(responseCode = "409", description = "`SLUG_ALREADY_EXISTS`: slug kullanımda.")
	public ResponseEntity<PublisherResponse> create(@Valid @RequestBody CreatePublisherRequest request) {
		PublisherResponse created = publisherAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	@Operation(operationId = "updatePublisher", summary = "Yayınevi güncelle",
			description = "Kısmi güncelleme; yalnızca ad değişirse slug korunur.")
	@ApiResponse(responseCode = "200", description = "Güncellenmiş yayınevi.")
	@ApiResponse(responseCode = "409", description = "`SLUG_ALREADY_EXISTS`: slug kullanımda.")
	public PublisherResponse update(@Parameter(description = "Yayınevi id'si") @PathVariable UUID id,
			@Valid @RequestBody UpdatePublisherRequest request) {
		return publisherAdminService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(operationId = "deletePublisher", summary = "Yayınevi sil")
	@ApiResponse(responseCode = "204", description = "Silindi.")
	@ApiResponse(responseCode = "409", description = "`RESOURCE_IN_USE`: yayınevine bağlı kitap var.")
	public ResponseEntity<Void> delete(@Parameter(description = "Yayınevi id'si") @PathVariable UUID id) {
		publisherAdminService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
