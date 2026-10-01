package com.kitapsepeti.catalog.controller.admin;

import java.net.URI;
import java.util.UUID;

import com.kitapsepeti.catalog.dto.request.AdminPageRequest;
import com.kitapsepeti.catalog.dto.request.CreatePublisherRequest;
import com.kitapsepeti.catalog.dto.request.UpdatePublisherRequest;
import com.kitapsepeti.catalog.dto.response.PageResponse;
import com.kitapsepeti.catalog.dto.response.PublisherResponse;
import com.kitapsepeti.catalog.service.PublisherAdminService;
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

/** Yayınevi yönetimi (SecurityConfig: /api/admin/** yalnızca ADMIN). */
@RestController
@RequestMapping(path = "/api/admin/publishers", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminPublisherController {

	private static final String BASE_PATH = "/api/admin/publishers/";

	private final PublisherAdminService publisherAdminService;

	public AdminPublisherController(PublisherAdminService publisherAdminService) {
		this.publisherAdminService = publisherAdminService;
	}

	@GetMapping
	public PageResponse<PublisherResponse> list(@Valid @ModelAttribute AdminPageRequest page) {
		return publisherAdminService.list(page);
	}

	@GetMapping("/{id}")
	public PublisherResponse get(@PathVariable UUID id) {
		return publisherAdminService.get(id);
	}

	@PostMapping
	public ResponseEntity<PublisherResponse> create(@Valid @RequestBody CreatePublisherRequest request) {
		PublisherResponse created = publisherAdminService.create(request);
		return ResponseEntity.created(URI.create(BASE_PATH + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	public PublisherResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePublisherRequest request) {
		return publisherAdminService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		publisherAdminService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
